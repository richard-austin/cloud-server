package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Cloud {
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    private boolean running = true;
    private final ExecutorService browserWriteExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService browserReadExecutor = Executors.newCachedThreadPool();
    private final ExecutorService splitMessagesExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sendToCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService startCloudProxyInputProcessExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService acceptConnectionsFromCloudProxyExecutor = Executors.newSingleThreadExecutor();

    final Map<Integer, SocketChannel> tokenSocketMap = new LinkedHashMap<>();

    private static final Logger logger = Logger.getLogger("CloudAsync");
    public static final int BUFFER_SIZE = 16000;
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    private SocketChannel cloudProxy;
    private final int browserFacingPort = 8083, cloudProxyFacingPort = 8081;

    public static void main(String[] args) {
        new Cloud().start();
    }

    private void start() {
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
                        updateSocketMap(browser, token);
                        readFromBrowser(browser, token);
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
        acceptConnectionsFromCloudProxyExecutor.execute(() -> {
            while (running) {
                try {
                    // Creating a ServerSocket to listen for connections with
                    ServerSocketChannel s = ServerSocketChannel.open();
                    s.bind(new InetSocketAddress(cloudProxyFacingPort));
                    while (running) {
                        try {
                            // It will wait for a connection on the local port
                            SocketChannel cloudProxy = s.accept();
                            cloudProxy.configureBlocking(true);
                            this.cloudProxy = cloudProxy;
                            remainsOfPreviousBuffer = null;
                            clearSocketMap();
                            startCloudProxyInputProcess();
                        } catch (IOException ioex) {
                            logger.severe("IOException in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                            reset();
                        }
                    }
                } catch (Exception ioex) {
                    logger.severe("Exception in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                    reset();
                }
            }
        });
    }

    private void startCloudProxyInputProcess() {
        startCloudProxyInputProcessExecutor.scheduleAtFixedRate(() -> {
            try {
                if (cloudProxy != null && cloudProxy.isOpen()) {
                    ByteBuffer buf = getBuffer();
                    while (cloudProxy.read(buf) != -1) {
                        splitMessages(buf);
                        buf = getBuffer();
                    }
                    recycle(buf);
                }
            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudProxyInputProcess");
                reset();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void sendResponseToCloudProxy(ByteBuffer buf) {
        sendToCloudProxyExecutor.submit(() -> {
            try {
                int result;
                do {
                    result = cloudProxy.write(buf);
                }
                while (result != -1 && buf.position() < buf.limit());
                recycle(buf);

            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudProxyOutputProcess");
                reset();
            }
        });
    }

    final void readFromBrowser(SocketChannel channel, final int token) {
        browserReadExecutor.submit(() -> {
            int result;
            ByteBuffer buf = getBuffer(token);
            try {
                buf.position(headerLength);
                while ((result = channel.read(buf)) != -1) {
                    int dataLength = 0;
                    dataLength += result;
                    setDataLength(buf, dataLength);
                    setBufferForSend(buf);
                    sendResponseToCloudProxy(buf);
                    buf = getBuffer(token);
                    buf.position(headerLength);
                }
                setConnectionClosedFlag(buf);
                // removeSocket(token);
                sendResponseToCloudProxy(buf);
            } catch (IOException ignored) {
               // setConnectionClosedFlag(buf);
                removeSocket(token);
               // sendResponseToCloudProxy(buf);
               // reset();
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                reset();
            }
        });
    }

    private void respondToBrowser(ByteBuffer buf) {
        // Dump the connection test heartbeats
        final String ignored = "Ignore";
        if (getToken(buf) == -1 && getDataLength(buf) == ignored.length()) {
            String strVal = new String(Arrays.copyOfRange(buf.array(), headerLength, buf.limit()), StandardCharsets.UTF_8);
            if (ignored.equals(strVal)) {
                return;
            }
        }

        browserWriteExecutor.submit(() -> {
            try {
//            logMessageMetadata(buf, "To browser");
                int token = getToken(buf);
                int length = getDataLength(buf);
                SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                if (getConnectionClosedFlag(buf) != 0) {
                    removeSocket(token);  // Usually already gone
                } else if (frontEndChannel == null)
                    throw new Exception("Couldn't find a socket for token " + token);
                else if (frontEndChannel.isOpen()) {
                    buf.position(headerLength);
                    buf.limit(headerLength + length);
                    int result;
                    try {
                        do {
                            result = frontEndChannel.write(buf);
                        }
                        while (result != -1 && buf.position() < buf.limit());
                    } catch (IOException ioex) {
                        logger.severe("IOException in respondToBrowser: "+ioex.getMessage());
                    }
                } else
                    logger.severe("Socket for token " + token + " was closed");
            } catch (Exception ex) {
                showExceptionDetails(ex, "respondToBrowser");
            }
        });
    }

    private int count = 0;
    private int lengthTotal = 0;
    private long checksumTotal = 0;

    private void logMessageMetadata(ByteBuffer buf, String title) {
        int position = buf.position();
        lengthTotal += getDataLength(buf);
        long checksum = getCRC32Checksum(buf);
        checksumTotal += checksum;
        boolean disconnect = getConnectionClosedFlag(buf) != 0;
        System.out.println(title + (disconnect ? "*" : ".") + ".   #: " + ++count + ", Token: " + getToken(buf) + ", Length: " + getDataLength(buf) + ", lengthTotal: " + lengthTotal + ", Checksum: " + checksum + ", ChecksumTotal: " + checksumTotal);
        buf.position(position);
    }

    private void removeSocket(int token) {
        logger.log(Level.INFO, "Removing socket for token " + token);
        tokenSocketMap.remove(token);
    }

    private synchronized void updateSocketMap(SocketChannel browser, int token) {
        tokenSocketMap.put(token, browser);
        List<Integer> tokens = new ArrayList<Integer>();
        tokenSocketMap.forEach((tok, socket) -> {
            if (socket != null && !(socket.isOpen() || socket.isConnected()))
                tokens.add(tok);
        });
        tokens.forEach(tokenSocketMap::remove);
    }

    private synchronized void clearSocketMap() {
        Set<Integer> tokens = new HashSet<>(tokenSocketMap.keySet());
        tokens.forEach((tok) -> {
            try {
                tokenSocketMap.get(tok).close();
                tokenSocketMap.remove(tok);
            } catch (Exception ignored) {
            }
        });
    }

    void showExceptionDetails(Throwable t, String functionName) {
        logger.log(Level.SEVERE, t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage() + "\n" + t.fillInStackTrace());
        for (StackTraceElement stackTraceElement : t.getStackTrace()) {
            System.err.println(stackTraceElement.toString());
        }
    }

    /**
     * getBuffer: Get a new ByteBuffer of BUFFER_SIZE bytes length.
     *
     * @return: The buffer
     */
    private ByteBuffer getBuffer() {
        ByteBuffer buf = Objects.requireNonNullElseGet(bufferQueue.poll(), () -> ByteBuffer.allocate(BUFFER_SIZE));
        buf.clear();
        return buf;
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
        buf.put((byte) 0); // Reserve space for the closed connection flag
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

    private void setConnectionClosedFlag(ByteBuffer buf) {
        buf.position(tokenLength + lengthLength);
        buf.put((byte) 1);
        setDataLength(buf, 0);
        buf.limit(headerLength);
        buf.position(0);
    }

    private byte getConnectionClosedFlag(ByteBuffer buf) {
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
        return getToken(buf, 0);
    }

    /**
     * getToken: Get the token at position
     *
     * @param buf:      Buffer to get the token from
     * @param position: Position at which to get the token
     * @return The token at position
     */
    private int getToken(ByteBuffer buf, int position) {
        int originalPosition = buf.position();
        buf.position(position);
        int token = buf.getInt();
        buf.position(originalPosition);
        return token;
    }

    public long getCRC32Checksum(ByteBuffer buf) {
        int length = getDataLength(buf);
        Checksum crc32 = new CRC32();
        crc32.update(buf.array(), 0, length + headerLength);
        return crc32.getValue();
    }

    void setBufferForSend(ByteBuffer buf) {
        buf.flip();
    }

    ByteBuffer remainsOfPreviousBuffer = null;

    void splitMessages(ByteBuffer buf) {
        splitMessagesExecutor.submit(() -> {
            try {
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
                                //     logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                                combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                                respondToBrowser(newBuf);
                            } catch (Exception ex) {
                                showExceptionDetails(ex, "splitMessages");
                            }
                        }
                    }
                }
                recycle(buf);
            } catch (Exception ex) {
                showExceptionDetails(ex, "splitMessages");
                reset();
            }
        });
    }

    void reset() {
        if (cloudProxy != null && cloudProxy.isOpen()) {
            try {
                cloudProxy.close();
            } catch (IOException ignored) {
            }
        }
        remainsOfPreviousBuffer = null;
        clearSocketMap();
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
    }

    int c = 0;

    void throwEx() throws Exception {
        if (c++ >= 20) {
            c = 0;
            throw new Exception("Contrived exception");
        }
    }
}

