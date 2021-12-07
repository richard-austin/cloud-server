package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class CloudProxyAsynch {

    private static final String webServerAddress = "192.168.0.29:443";
    public final int cloudListeningPort;
    public final String cloudHost;
    private final String webserverHost;
    private final int webserverPort;
    final Map<Integer, AsynchronousSocketChannel> tokenSocketMap = new LinkedHashMap<>();
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;

    private AsynchronousSocketChannel cloudSocket;

    public static final int BUFFER_SIZE = 16000;

    private static final Logger logger = Logger.getLogger("CloudProxyAsynch");

    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();

    CloudProxyAsynch(String webServerHost, int webServerPort, String cloudHost, int cloudListeningPort) {
        this.webserverHost = webServerHost;
        this.webserverPort = webServerPort;
        this.cloudHost = cloudHost;
        this.cloudListeningPort = cloudListeningPort;
    }

    public static void main(String[] args) {
        final String host;
        final int port;
        try {
            String[] split = webServerAddress.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("host:port");
            }
            host = split[0];
            port = Integer.parseInt(split[1]);
            new CloudProxyAsynch(host, port, "localhost", 8081).start();
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    static final Object LOCK = new Object();

    void start() {
        createConnectionToCloud();
        startReadFromCloud();

        try {
            synchronized (LOCK) {
                LOCK.wait(0, 0);
            }
        } catch (InterruptedException iex) {
            logger.log(Level.WARNING, "Process interrupted: " + iex.getMessage());
        }
    }

    private void createConnectionToCloud() {
        try {
            final AsynchronousSocketChannel cloudChannel = AsynchronousSocketChannel.open();
            cloudChannel.connect(new InetSocketAddress(cloudHost, cloudListeningPort), cloudChannel, new CompletionHandler<>() {
                @Override
                public void completed(final Void nothing, AsynchronousSocketChannel client) {
                    setCloudChannel(cloudChannel);
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                    logger.log(Level.INFO, "Exception in createConnectionToCloud: " + exc.getClass().getName() + ": " + exc.getMessage());
                }
            });

        } catch (Exception e) {
            logger.log(Level.INFO, "Exception in createConnectionToCloud: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void setCloudChannel(AsynchronousSocketChannel cloudChannel) {
        this.cloudSocket = cloudChannel;
    }

    void startReadFromCloud() {
        AtomicBoolean runAgain = new AtomicBoolean(true);
        readFromCloud(runAgain);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (runAgain.get()) {
                runAgain.set(false);
                readFromCloud(runAgain);
            }
        }, 300, 1, TimeUnit.MILLISECONDS);
    }

    void readFromCloud(AtomicBoolean runAgain) {
        if (cloudSocket != null && cloudSocket.isOpen()) {
            ByteBuffer buf = getBuffer();
            cloudSocket.read(buf, null, new CompletionHandler<Integer, AtomicBoolean>() {
                @Override
                public void completed(Integer result, AtomicBoolean doAgain) {
                    if (result != -1) {
                        splitMessages(buf);
                        buf.clear();
                        readFromCloud(runAgain);
                    } else
                        runAgain.set(true);
                }

                @Override
                public void failed(Throwable exc, AtomicBoolean doAgain) {
                    logger.log(Level.INFO, "readFromCloud failed: " + exc.getClass().getName() + " : " + exc.getMessage());
                    runAgain.set(true);
                }
            });
        }
    }

    void writeRequestsToWebserver(ByteBuffer buf) {
        int token = getToken(buf);
        if (tokenSocketMap.containsKey(token)) {
            writeRequestToWebserver(buf, tokenSocketMap.get(token), token);
        } else  // Make a new connection to the webserver
        {
            try {
                tokenSocketMap.put(token, null);
                final AsynchronousSocketChannel webserverChannel = AsynchronousSocketChannel.open();
                webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort), token, new CompletionHandler<Void, Integer>() {
                    @Override
                    public void completed(Void result, Integer token) {
                        tokenSocketMap.put(token, webserverChannel);
                        writeRequestToWebserver(buf, webserverChannel, token);
                    }

                    @Override
                    public void failed(Throwable exc, Integer token) {
                    }
                });
            } catch (IOException ioex) {
                logger.log(Level.INFO, "writeRequestsToWebserver failed: " + ioex.getClass().getName() + " : " + ioex.getMessage());
            }
        }
    }

    void writeRequestToWebserver(final ByteBuffer buf, final AsynchronousSocketChannel webserverChannel, int token) {
        final AtomicInteger writeTotal = new AtomicInteger(headerLength);
        int length = getDataLength(buf);
        buf.position(headerLength);
        buf.limit(headerLength + length);
        writeRequestToWebserver(buf, webserverChannel, token, writeTotal);
    }

    void writeRequestToWebserver(final ByteBuffer buf, final AsynchronousSocketChannel webserverChannel, int token, AtomicInteger writeTotal) {
        webserverChannel.write(buf, token, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Integer token) {
                if (result != -1) {
                    if (writeTotal.addAndGet(result) < buf.limit())
                        writeRequestToWebserver(buf, webserverChannel, token, writeTotal);
                    else
                        readResponseFromWebserver(webserverChannel, token);
                }
            }

            @Override
            public void failed(Throwable exc, Integer token) {
                logger.log(Level.INFO, "writeRequestToWebserver failed: " + exc.getClass().getName() + " : " + exc.getMessage());
            }
        });
    }

    private void readResponseFromWebserver(AsynchronousSocketChannel webserverChannel, int token) {
        ByteBuffer buf = getBuffer(token);
        webserverChannel.read(buf, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void nothing) {
                if (result != -1) {
                    setDataLength(buf, result);
//                    logger.log(Level.INFO, "readResponseFromWebserver: "+log(buf));
                    setBufferForSend(buf);
                    sendResponseToCloud(buf);
                    readResponseFromWebserver(webserverChannel, token);
                }
                else {
                    flagConnectionClosed(buf);
                    setBufferForSend(buf);
                    sendResponseToCloud(buf);
                }
            }

            @Override
            public void failed(Throwable exc, Void nothing) {
            }
        });
    }

    final Object sendResponseToCloudLock = new Object();

    private void sendResponseToCloud(ByteBuffer buf) {
        synchronized (sendResponseToCloudLock) {
            AtomicInteger writeTotal = new AtomicInteger(0);
            sendResponseToCloud(buf, writeTotal);
        }
    }

    private void sendResponseToCloud(ByteBuffer buf, AtomicInteger writeTotal) {
        cloudSocket.write(buf, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result != -1)
                    if (writeTotal.addAndGet(result) < buf.limit())
                        sendResponseToCloud(buf, writeTotal);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {

            }
        });
    }

    /**
     * getBuffer: Get a new ByteBuffer of BUFFER_SIZE bytes length.
     *
     * @return: The buffer
     */
    private ByteBuffer getBuffer() {
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
        buf.put((byte)0); // Reserve space for the closed connection flag
        return buf;
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
        buf.put((byte)1);
        buf.position(position);
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
                        combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                        writeRequestsToWebserver(newBuf);
                    } catch (Exception ex) {
                        Object x = ex;
                    }
                }
            }
        }
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
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
