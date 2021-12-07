package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Cloud {
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    final Queue<ByteBuffer> messageOutQueue = new ConcurrentLinkedQueue<>();
    //    AsynchronousSocketChannel clientSocket;
    private boolean running = true;
    final Map<Integer, SocketChannel> tokenSocketMap = new LinkedHashMap<>();

    private static final Logger logger = Logger.getLogger("CloudAsync");
    public static final int BUFFER_SIZE = 16000;
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    private SocketChannel cloudProxy;

    public static void main(String[] args) {
        new Cloud().start(8082, 8081);
    }

    private void start(final int browserFacingPort, final int cloudProxyFacingPort) {
        acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
        acceptConnectionsFromBrowser(browserFacingPort); // Never returns
    }

    private void acceptConnectionsFromBrowser(final int browserFacingPort) {
        while (running) {
            try {
                // Creating a ServerSocket to listen for connections with
                ServerSocketChannel s = ServerSocketChannel.open();
                s.bind(new InetSocketAddress(browserFacingPort));
                while (running) {
                    try {
                        // It will wait for a connection on the local port
                        SocketChannel browser = s.accept();
                        browser.configureBlocking(true);
                        final int token = getToken();
                        tokenSocketMap.put(token, browser);

                        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
                        executor.execute(() -> readFromBrowser(browser, token));
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "Exception in acceptConnectionsFromBrowser: " + ex.getClass().getName() + ": " + ex.getMessage());
                    }
                }
            } catch (IOException ioex) {
                logger.log(Level.SEVERE, "IOException in acceptConnectionsFromBrowser: " + ioex.getClass().getName() + ": " + ioex.getMessage());
            }
        }
    }

    private void acceptConnectionsFromCloudProxy(final int cloudProxyFacingPort) {
        startCloudProxyOutputProcess();
        startCloudProxyInputProcess();
        Executors.newSingleThreadExecutor().execute(() -> {
            while (running) {
                try {
                    // Creating a ServerSocket to listen for connections with
                    ServerSocketChannel s = ServerSocketChannel.open();
                    s.bind(new InetSocketAddress(cloudProxyFacingPort));
                    while (running) {
                        try {
                            // It will wait for a connection on the local port
                            cloudProxy = s.accept();
                            cloudProxy.configureBlocking(true);

                            //requestProcessing(cloudProxy, server, host, remoteport);
                        } catch (Exception ex) {
                            logger.log(Level.SEVERE, "Exception in acceptConnectionsFromCloudProxy: " + ex.getClass().getName() + ": " + ex.getMessage());
                        }
                    }
                } catch (IOException ioex) {
                    logger.log(Level.SEVERE, "IOException in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                }
            }
        });
    }

    private void startCloudProxyInputProcess() {
        final AtomicBoolean busy = new AtomicBoolean(false);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (cloudProxy != null && cloudProxy.isOpen() && !busy.get()) {
                busy.set(true);
                ByteBuffer buf = getBuffer();
                try {
                    while (cloudProxy.read(buf) != -1) {
                        splitMessages(buf);
                        buf.clear();
                    }
                } catch (Exception ex) {
                    showExceptionDetails(ex, "startCloudProxyInputProcess");
                }
                busy.set(false);
                recycle(buf);
            }
        }, 300, 1, TimeUnit.MILLISECONDS);
    }

    final Object startCloudProxyOutputProcessLock = new Object();

    private void startCloudProxyOutputProcess() {
        Executors.newSingleThreadExecutor().execute(() -> {
            synchronized (startCloudProxyOutputProcessLock) {
                try {
                    while (running) {
                        startCloudProxyOutputProcessLock.wait();
                        while (!messageOutQueue.isEmpty()) {
                            ByteBuffer buf = messageOutQueue.poll();
                            int result;
                            do {
                                result = cloudProxy.write(buf);
                            }
                            while (result != -1 && buf.position() < buf.limit());
                            recycle(buf);
                        }
                    }
                } catch (Exception ex) {
                    showExceptionDetails(ex, "startCloudProxyOutputProcess");
                }
            }
        });
    }

    final void readFromBrowser(SocketChannel channel, final int token) {
        int result;
        try {
            ByteBuffer buf = getBuffer(token);
            buf.position(headerLength);
            while ((result = channel.read(buf)) != -1) {
                int dataLength = 0;
                dataLength += result;
                setDataLength(buf, dataLength);
                setBufferForSend(buf);
                messageOutQueue.add(buf);
                synchronized (startCloudProxyOutputProcessLock) {
                    startCloudProxyOutputProcessLock.notify();
                }
                buf = getBuffer(token);
                buf.position(headerLength);
            }
            recycle(buf);
        } catch (Exception ex) {
            showExceptionDetails(ex, "readFromBrowser");
        }
    }

    private void respondToBrowser(ByteBuffer buf) {
        try {
            int token = getToken(buf);
            int length = getDataLength(buf);
            SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
            if (frontEndChannel == null)
                throw new Exception("Couldn't find a socket for token " + token);
            else if(getConnectionClosedFlag(buf) != 0) {
                frontEndChannel.close();
                tokenSocketMap.remove(token);
            }
            else if (frontEndChannel.isOpen()){
                buf.position(headerLength);
                buf.limit(headerLength + length);
                int result;
                try {
                    do {
                        result = frontEndChannel.write(buf);
                    }
                    while (result != -1 && buf.position() < buf.limit());

                } catch (IOException ioex) {
                    showExceptionDetails(ioex, "respondToFrontEnd");
                }
            }
            else
                logger.log(Level.SEVERE, "Socket for token "+token+" was closed");
        } catch (Exception ex) {
            showExceptionDetails(ex, "respondToBrowser");
        }
    }

    void showExceptionDetails(Throwable t, String functionName) {
        logger.log(Level.SEVERE, t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage() + "\n" + t.fillInStackTrace());
    }

    /**
     * getBuffer: Get a new ByteBuffer of BUFFER_SIZE bytes length.
     *
     * @return: The buffer
     */
    private ByteBuffer getBuffer() {
        logger.log(Level.INFO, "Number of uffers in queue = "+bufferQueue.size());
        ByteBuffer poll = bufferQueue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }

    /**
     * getBuffer: Get a buffer and place the token at the start. Reserve a further lengthLength bytes to contain the length.
     *
     * @param token: The token
     * @return: The byte buffer with the token in place and length reservation set up.
     */
    private ByteBuffer getBuffer(int token) {
        ByteBuffer buf = getBuffer();

        buf.putInt(token);
        buf.putInt(0);  // Reserve space for the data length
        buf.put((byte)0); // Reserve space for the checksum
        return buf;
    }

    private void recycle(ByteBuffer buf) {
        buf.clear();
        bufferQueue.add(buf);
    }

    /**
     * setDataLength: Set the lengthLength bytes following the token to the length of the data in the buffer
     * (minus token and length bytes).
     *
     * @param buf:    The buffer to set the length in.
     * @param length: The length to set.
     */
    private void setDataLength(ByteBuffer buf, int length) {
        int position = buf.position();
        buf.position(tokenLength);
        buf.putInt(length);
        buf.position(position);
    }

    /**
     * getDataLength: Get the length of the data from the buffer. The actual data follows the token and length bytes.
     *
     * @param buf: The buffer
     * @return: The length of the data in the buffer
     */
    private int getDataLength(ByteBuffer buf) {
        final int position = buf.position();
        buf.position(tokenLength);
        int length = buf.getInt();
        buf.position(position); // Leave the position where the data starts.
        return length;
    }

    static int nextToken = 0;
    final Object getTokenLock = new Object();

    /**
     * getToken: Get a sequential integer as a token
     *
     * @return: The token as an integer
     */
    private int getToken() {
        synchronized (getTokenLock) {
            return ++nextToken;
        }
    }

    public long getCRC32Checksum(ByteBuffer buf) {
        Checksum crc32 = new CRC32();
        crc32.update(buf.array(), headerLength, buf.limit() - headerLength);
        return crc32.getValue();
    }

    private long getConnectionClosedFlag(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        byte flag = buf.get();
        buf.position(position);
        return flag;
    }

    /**
     * getToken: Get the token in the ByteBuffer
     *
     * @param buf: The buffer containing the token.
     * @return: The token
     */
    private int getToken(ByteBuffer buf) {
        int position = buf.position();
        buf.position(0);
        int token = buf.getInt();
        buf.position(position);
        return token;
    }

    void setBufferForSend(ByteBuffer buf) {
        buf.flip();
    }

    ByteBuffer remainsOfPreviousBuffer = null;

    void splitMessages(ByteBuffer buf) {
        buf.flip();
        ByteBuffer combinedBuf;

        if (remainsOfPreviousBuffer != null) {
            // Append the new buffer onto the previous ones remaining content
            combinedBuf = ByteBuffer.allocate(buf.limit() + remainsOfPreviousBuffer.limit() - remainsOfPreviousBuffer.position());
            combinedBuf.put(remainsOfPreviousBuffer);
            combinedBuf.put(buf);
            remainsOfPreviousBuffer = null;
        } else
            combinedBuf = buf;
        combinedBuf.rewind();

        while (combinedBuf.position() < combinedBuf.limit()) {
            if (combinedBuf.limit() - combinedBuf.position() < headerLength) {
                remainsOfPreviousBuffer = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
                combinedBuf.position(combinedBuf.limit());
            } else {
                int lengthThisMessage = getMessageLengthFromPosition(combinedBuf);
                if (lengthThisMessage > combinedBuf.limit() - combinedBuf.position()) {
                    remainsOfPreviousBuffer = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
                    combinedBuf.position(combinedBuf.limit());
                } else {
                    try {
                        ByteBuffer newBuf = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.position() + lengthThisMessage));
                        newBuf.rewind();
                        logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                        combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                        respondToBrowser(newBuf);
                    } catch (Exception ex) {
                        showExceptionDetails(ex, "splitMessages");
                    }
                }
            }
        }
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
    }
}

