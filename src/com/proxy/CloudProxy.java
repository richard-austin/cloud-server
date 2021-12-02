package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudProxy {

    private static final String webServerAddress = "192.168.0.29:443";
    public final int cloudListeningPort;
    public final String cloudHost;
    private final String webserverHost;
    private final int webserverPort;
    final Map<String, AsynchronousSocketChannel> tokenSocketMap = new LinkedHashMap<>();
    private final int tokenLength = 36;
    private AsynchronousSocketChannel cloudSocket;
    final Queue<ByteBuffer> outQueue = new ConcurrentLinkedQueue<>();

    public static final int BUFFER_SIZE = 3000;

    private static final Logger logger = Logger.getLogger("CloudProxy");

    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    final private Queue<AsynchronousSocketChannel> cloudConnectionQueue = new ConcurrentLinkedQueue<>();

    CloudProxy(String webServerHost, int webServerPort, String cloudHost, int cloudListeningPort) {
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
            new CloudProxy(host, port, "localhost", 8081).start();
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    static final Object LOCK = new Object();

    void start() {
        createConnectionToCloud();
        startReadFromCloud();
        startSendResponseToCloud();

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
                        readFromCloud(runAgain);
                    } else
                        runAgain.set(true);
                }

                @Override
                public void failed(Throwable exc, AtomicBoolean doAgain) {
                    logger.log(Level.INFO, "readFromCloudProxy failed: " + exc.getClass().getName() + " : " + exc.getMessage());
                    runAgain.set(true);
                }
            });
        }
    }

    //    void startWriteRequestsToWebserver() {
//        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
//        executor.scheduleAtFixedRate(this::writeRequestsToWebserver, 300, 1, TimeUnit.MILLISECONDS);
//    }
//
    void writeRequestsToWebserver(ByteBuffer buf) {
        String token = getToken(buf);
        if (tokenSocketMap.containsKey(token)) {
//                    try {
//                        while (tokenSocketMap.get(token) == null)
//                            Thread.sleep(1);
//                    } catch (InterruptedException ex) {
//                    }
            writeRequestToWebserver(buf, tokenSocketMap.get(token));
        } else  // Make a new connection to the webserver
        {
            try {
                tokenSocketMap.put(token, null);
                final AsynchronousSocketChannel webserverChannel = AsynchronousSocketChannel.open();
                webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort), token, new CompletionHandler<>() {

                    @Override
                    public void completed(Void result, String token) {
                        tokenSocketMap.put(token, webserverChannel);
                        writeRequestToWebserver(buf, webserverChannel);
                        readResponseFromWebserver(webserverChannel, token);
                    }

                    @Override
                    public void failed(Throwable exc, String token) {

                    }
                });
            } catch (IOException ioex) {
                logger.log(Level.INFO, "readFromCloudProxy failed: " + ioex.getClass().getName() + " : " + ioex.getMessage());
            }
        }
    }

    void writeRequestToWebserver(final ByteBuffer buf, final AsynchronousSocketChannel webserverChannel) {
        synchronized (webserverChannel) {
            int length = getDataLength(buf);
            buf.position(tokenLength + Integer.BYTES);
            buf.limit(tokenLength + Integer.BYTES + length);
            webserverChannel.write(buf, null, new CompletionHandler<>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    //                   logger.log(Level.INFO, "writeRequestToWebserver: "+log(buf));
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    logger.log(Level.INFO, "startRespondToFrontEnd failed: " + exc.getClass().getName() + " : " + exc.getMessage());
                }
            });
        }
    }

    private void readResponseFromWebserver(AsynchronousSocketChannel webserverChannel, String token) {
        ByteBuffer buf = getBuffer(token);
        webserverChannel.read(buf, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void nothing) {
                if (result > 0) {
                    setDataLength(buf, result);
//                    logger.log(Level.INFO, "readResponseFromWebserver: "+log(buf));
                    outQueue.add(buf);
                    readResponseFromWebserver(webserverChannel, token);
                }
            }

            @Override
            public void failed(Throwable exc, Void nothing) {

            }
        });
    }

    void startSendResponseToCloud() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::sendResponseToCloud, 300, 1, TimeUnit.MILLISECONDS);
    }

    private void sendResponseToCloud() {
        while (!outQueue.isEmpty()) {
            ByteBuffer buf = outQueue.poll();
            setBufferForSend(buf);
            cloudSocket.write(buf, null, new CompletionHandler<>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    // Nothing more to do for now
                }

                @Override
                public void failed(Throwable exc, Object attachment) {

                }
            });
        }
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
     * getBuffer: Get a buffer and place the token at the start. Reserve a further Integer.BYTES bytes to contain the length.
     *
     * @param token: The token
     * @return: The byte buffer with the token in place and length reservation set up.
     */
    private ByteBuffer getBuffer(String token) {
        ByteBuffer buf = getBuffer();

        buf.put(token.getBytes());
        buf.putInt(0);  // Reserve space for the data length
        return buf;
    }

    /**
     * setDataLength: Set the Integer.BYTES bytes following the token to the length of the data in the buffer
     * (minus token and length bytes).
     *
     * @param buf:    The buffer to set the length in.
     * @param length: The length to set.
     */
    private void setDataLength(ByteBuffer buf, int length) {
//        if(length > (BUFFER_SIZE-tokenLength-Integer.BYTES) || length < 0)
//        {
//            logger.log(Level.SEVERE, "Crazy length value ("+length+"}");
//        }

        int position = buf.position();
        buf.position(tokenLength);
        // Set apparent size to full buffer size so that "packets" are all the same size
//        buf.limit(BUFFER_SIZE);
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
        int length = buf.getInt(tokenLength);
        buf.position(tokenLength + Integer.BYTES);
        return length;
    }

    /**
     * getToken: Get the token in the ByteBuffer
     *
     * @param buf: The buffer containing the token.
     * @return: The token
     */
    private String getToken(ByteBuffer buf) {
        int position = buf.position();
        buf.position(0);
        byte[] bytes = new byte[36];
        buf.get(bytes, 0, 36);
        buf.position(position);
        return new String(bytes);
    }

    void setBufferForSend(ByteBuffer buf) {
        buf.flip();
        //      buf.limit(BUFFER_SIZE);
    }

    ByteBuffer previousBuffer = null;

    void splitMessages(ByteBuffer buf) {
        final int headerLength = tokenLength + Integer.BYTES;
        buf.flip();
        ByteBuffer combinedBuf;

        if (previousBuffer != null) {
            // Append the new buffer onto the previous ones remaining content
            combinedBuf = ByteBuffer.allocate(buf.limit() + previousBuffer.limit() - previousBuffer.position());
            combinedBuf.put(previousBuffer);
            combinedBuf.put(buf);
            previousBuffer = null;
        } else
            combinedBuf = buf;
        combinedBuf.rewind();

        while (combinedBuf.position() < combinedBuf.limit()) {
            if (combinedBuf.limit() - combinedBuf.position() < headerLength) {
                previousBuffer = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
                combinedBuf.position(combinedBuf.limit());
            } else {
                int lengthThisMessage = getMessageLengthFromPosition(combinedBuf);
                if (lengthThisMessage > combinedBuf.limit() - combinedBuf.position()) {
                    previousBuffer = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
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
        return buf.getInt(buf.position() + tokenLength) + tokenLength + Integer.BYTES;
    }

    private String log(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + Integer.BYTES);

        int length = getDataLength(buf);
        byte[] dataBytes = new byte[length];
        for (int i = 0; i < length; ++i)
            dataBytes[i] = buf.get();
        buf.position(position);
        return new String(dataBytes);
    }

    static final class WebServerChannels extends LinkedHashMap<String, AsynchronousSocketChannel> {
    }
}
