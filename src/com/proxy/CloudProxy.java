package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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

public class CloudProxy {

    final Map<Integer, SocketChannel> tokenSocketMap = new LinkedHashMap<>();
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    public static final int BUFFER_SIZE = 16000;
    private static final Logger logger = Logger.getLogger("CloudProxy");
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    final Queue<ByteBuffer> messageOutQueue = new ConcurrentLinkedQueue<>();
    SocketChannel cloudChannel;
    private boolean running = true;
    private final String webserverHost;
    private final int webserverPort;
    private final String cloudHost;
    private final int cloudPort;

    CloudProxy(String webServerHost, int webServerPort, String cloudHost, int cloudPort) {
        this.webserverHost = webServerHost;
        this.webserverPort = webServerPort;
        this.cloudHost = cloudHost;
        this.cloudPort = cloudPort;
    }

    public static void main(String[] args) {
        new CloudProxy("192.168.0.29", 443, "localhost", 8081).start();
    }

    final Object LOCK = new Object();

    void start() {
        createConnectionToCloud();
        startCloudOutputProcess();
        synchronized (LOCK) {
            try {
                LOCK.wait();
            } catch (InterruptedException e) {
                showExceptionDetails(e, "start");
            }
        }
    }

    private void createConnectionToCloud() {
        try {
            SocketChannel cloudChannel = SocketChannel.open();
            cloudChannel.connect(new InetSocketAddress(cloudHost, cloudPort));
            cloudChannel.configureBlocking(true);
            this.cloudChannel = cloudChannel;
            startCloudInputProcess(cloudChannel);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startCloudInputProcess(SocketChannel cloudChannel) {
        final AtomicBoolean busy = new AtomicBoolean(false);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (cloudChannel == null || !cloudChannel.isConnected() || !cloudChannel.isOpen()) {
                executor.shutdown();
                createConnectionToCloud();
            } else if (!busy.get()) {
                busy.set(true);
                ByteBuffer buf = getBuffer();
                try {
                    while (cloudChannel.read(buf) != -1) {
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

    private void startCloudOutputProcess() {
        Executors.newSingleThreadExecutor().execute(() -> {
            synchronized (startCloudProxyOutputProcessLock) {
                try {
                    while (running) {
                        startCloudProxyOutputProcessLock.wait();
                        while (!messageOutQueue.isEmpty()) {
                            ByteBuffer buf = messageOutQueue.poll();
                            int result;
                            do {
                                result = cloudChannel.write(buf);
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


    private void writeRequestToWebserver(ByteBuffer buf) {
        int token = getToken(buf);
        if (tokenSocketMap.containsKey(token)) {
            writeRequestToWebserver(buf, tokenSocketMap.get(token), token);
        } else  // Make a new connection to the webserver
        {
            try {
                final SocketChannel webserverChannel = SocketChannel.open();
                webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort));
                webserverChannel.configureBlocking(true);
                tokenSocketMap.put(token, webserverChannel);
                writeRequestToWebserver(buf, webserverChannel, token);
            } catch (IOException ioex) {
                showExceptionDetails(ioex, "writeRequestsToWebserver");
            }
        }
    }

    private void writeRequestToWebserver(final ByteBuffer buf, final SocketChannel webserverChannel, int token) {
        Executors.newSingleThreadExecutor().execute(() -> {
            int length = getDataLength(buf);
            buf.position(headerLength);
            buf.limit(headerLength + length);
            int result;
            try {
                do {
                    result = webserverChannel.write(buf);
                }
                while (result != -1 && buf.position() < buf.limit());
            } catch (IOException e) {
                showExceptionDetails(e, "writeRequestToWebserver");
            }
            readResponseFromWebserver(webserverChannel, token);
        });
    }

    private void readResponseFromWebserver(SocketChannel webserverChannel, int token) {
        ByteBuffer buf;
        int result;
        try {
            do {
                buf = getBuffer(token);
                result = webserverChannel.read(buf);
                setDataLength(buf, buf.position() - headerLength);
//                    logger.log(Level.INFO, "readResponseFromWebserver: "+log(buf));
                if (result == -1)
                    flagConnectionClosed(buf);
                setBufferForSend(buf);
                sendResponseToCloud(buf);
            }
            while (result != -1);
        } catch (IOException e) {
            showExceptionDetails(e, "readResponseFromWebserver");
        }
    }

    private void sendResponseToCloud(ByteBuffer buf) {
        messageOutQueue.add(buf);
        synchronized (startCloudProxyOutputProcessLock) {
            startCloudProxyOutputProcessLock.notify();
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

    private void flagConnectionClosed(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        buf.put((byte) 1);
        buf.position(position);
    }

    private long getConnectionClosedFlag(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        byte flag = buf.get();
        buf.position(position);
        return flag;
    }

    /**
     * getDataLength: Get the length of the data from the buffer. The actual data follows the token and length bytes.
     *
     * @param buf: The buffer
     * @return: The length of the data in the buffer
     */
    private int getDataLength(ByteBuffer buf) {
        int length = buf.getInt(tokenLength);
        buf.position(tokenLength + lengthLength);
        return length;
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

    public long getCRC32Checksum(ByteBuffer buf) {
        Checksum crc32 = new CRC32();
        crc32.update(buf.array(), headerLength, buf.limit() - headerLength);
        return crc32.getValue();
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
                        writeRequestToWebserver(newBuf);
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

    void showExceptionDetails(Throwable t, String functionName) {
        logger.log(Level.SEVERE, t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage() + "\n");
        for (StackTraceElement stackTraceElement : t.getStackTrace()) {
            System.err.println(stackTraceElement.toString());
        }
    }

    private String log(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);

        int length = getDataLength(buf);
        byte[] dataBytes = new byte[length];
        for (int i = 0; i < length; ++i)
            dataBytes[i] = buf.get();
        buf.position(position);
        return new String(dataBytes);
    }
}
