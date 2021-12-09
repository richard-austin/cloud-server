package com.proxy.Async;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

class CloudAsync {
    int frontEndFacingPort;
    int clientFacingPort;
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int checkSumLength = Long.BYTES;
    private final int headerLength = tokenLength + lengthLength + checkSumLength;

    public static final int BUFFER_SIZE = 16000;
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    AsynchronousSocketChannel clientSocket;

    final Map<Integer, AsynchronousSocketChannel> tokenSocketMap = new LinkedHashMap<>();

    private static final Logger logger = Logger.getLogger("CloudAsync");

    public static void main(String[] args) {
        new CloudAsync(8082, 8081).start();
    }

    CloudAsync(int frontEndFacingPort, int clientFacingPort) {
        this.frontEndFacingPort = frontEndFacingPort;
        this.clientFacingPort = clientFacingPort;
    }

    static final Object LOCK = new Object();

    void start() {
        openServerSocketForCloudProxy();
        acceptConnectionsFromFrontEnd();
        startReadFromCloudProxy();
        try {
            synchronized (LOCK) {
                LOCK.wait(0, 0);
            }
        } catch (InterruptedException iex) {
            logger.log(Level.WARNING, "Process interrupted: " + iex.getMessage());
        }
    }

    void openServerSocketForCloudProxy() {
        try {
            AsynchronousServerSocketChannel openToClientProxy = AsynchronousServerSocketChannel.open();
            final AsynchronousServerSocketChannel listenerToClientProxy =
                    openToClientProxy.bind(new InetSocketAddress(clientFacingPort));

            // Open up a listening port for the ClientProxy on the client machine to connect to
            // The client proxy should make a few connections
            listenerToClientProxy.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel client, Void nothing) {
                    // accept the next connection
                    listenerToClientProxy.accept(null, this);
                    clientSocket = client;
                }

                @Override
                public void failed(Throwable exc, Void nothing) {
                    logger.log(Level.SEVERE, "Exception in CloudAsync.start.listener.accept: " + exc.getMessage());
                }
            });
        } catch (IOException ioex) {
            logger.log(Level.INFO, "IOException in openServerSocketForCloudProxy: " + ioex.getClass().getName() + " " + ioex.getMessage());
        }
    }

    void acceptConnectionsFromFrontEnd() {
        try {
            AsynchronousServerSocketChannel openToFrontEnd = AsynchronousServerSocketChannel.open();
            final AsynchronousServerSocketChannel listenerToFrontEnd =
                    openToFrontEnd.bind(new InetSocketAddress(frontEndFacingPort));
            // Open up a listening port for the front end to connect to
            listenerToFrontEnd.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel frontEnd, Void nothing) {
                    // accept the next connection
                    listenerToFrontEnd.accept(null, this);
                    logger.log(Level.INFO, "CloudAsync.start accepted connection");

                    if (frontEnd != null) {
                        int token = getToken();
                        tokenSocketMap.put(token, frontEnd);
                        readFromFrontEnd(frontEnd, token);
                    } else
                        logger.log(Level.SEVERE, "accept socket was null");
                }

                @Override
                public void failed(Throwable exc, Void nothing) {
                    System.out.println("Exception in CloudAsync.start.listener.accept: " + exc.getMessage());
                }
            });
        } catch (IOException ioex) {
            logger.log(Level.INFO, "IOException in openServerSocketForFrontEnd: " + ioex.getClass().getName() + " " + ioex.getMessage());
        }
    }

    final void readFromFrontEnd(AsynchronousSocketChannel channel, final int token) {
        ByteBuffer buf = getBuffer(token);
        channel.read(buf, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result != -1) {
                    setDataLength(buf, result);
                    setCRC32Checksum(buf);
                    setBufferForSend(buf);
                    startWriteToCloudProxy(buf);
                } else
                    return;

                readFromFrontEnd(channel, token);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                logger.log(Level.INFO, "Failed read: " + exc.getMessage());
            }
        });
    }

    final Object startWriteToCloudProxyLock = new Object();

    void startWriteToCloudProxy(ByteBuffer buf) {
        synchronized (startWriteToCloudProxyLock) {
            AtomicInteger writeTotal = new AtomicInteger(0);
            startWriteToCloudProxy(buf, writeTotal);
        }
    }

    void startWriteToCloudProxy(ByteBuffer buf, AtomicInteger writeTotal) {
        try {
            if (clientSocket != null) {
                //                    logger.log(Level.INFO, "startWriteToCloudProxy: " + log(buf));
                clientSocket.write(buf, null, new CompletionHandler<>() {
                    @Override
                    public void completed(Integer result, Object attachment) {
                        if (result != -1)
                            if (writeTotal.addAndGet(result) < buf.limit())
                                startWriteToCloudProxy(buf, writeTotal);
                    }

                    @Override
                    public void failed(Throwable exc, Object attachment) {
                        logger.log(Level.INFO, "startWriteToCloudProxy failed: " + exc.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "Exception in startWriteToCloudProxy: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    void startReadFromCloudProxy() {
        AtomicBoolean done = new AtomicBoolean(true);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (done.get()) {
                ByteBuffer buf = getBuffer();
                readFromCloudProxy(done, buf);
            }
        }, 300, 1, TimeUnit.MILLISECONDS);
    }

    void readFromCloudProxy(AtomicBoolean done, ByteBuffer buf) {
        if (clientSocket != null && clientSocket.isOpen() && done.get()) {
            done.set(false);
            clientSocket.read(buf, done, new CompletionHandler<>() {
                @Override
                public void completed(Integer result, AtomicBoolean done) {
                    if (result != -1) {
//                        logger.log(Level.INFO, "readFromCloudProxy: " + log(buf));
                        splitMessages(buf);
                        buf.clear();
                        readFromCloudProxy(done, buf);
                    }
                    done.set(true);
                }

                @Override
                public void failed(Throwable exc, AtomicBoolean done) {
                    logger.log(Level.INFO, "readFromCloudProxy failed: " + exc.getClass().getName() + " : " + exc.getMessage());
                    done.set(true);
                }
            });
        }
    }

    void respondToFrontEnd(ByteBuffer buf) {
        int token = getToken(buf);
        int length = getDataLength(buf);
        AsynchronousSocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
        buf.position(headerLength);
        buf.limit(headerLength + length);

        AtomicInteger writeTotal = new AtomicInteger(headerLength);
        respondToFrontEnd(buf, writeTotal, frontEndChannel);
    }

    void respondToFrontEnd(ByteBuffer buf, AtomicInteger writeTotal, AsynchronousSocketChannel frontEndChannel) {
        frontEndChannel.write(buf, null, new CompletionHandler<>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result != -1)
                    if (writeTotal.addAndGet(result) < buf.limit())
                        respondToFrontEnd(buf, writeTotal, frontEndChannel);
                // Done, nothing more to do
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                logger.log(Level.INFO, "startRespondToFrontEnd failed: " + exc.getClass().getName() + " : " + exc.getMessage());
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
        buf.putLong(0); // Reserve space for the checksum
        return buf;
    }

    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
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

    private void setCRC32Checksum(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        buf.putLong(getCRC32Checksum(buf));
        buf.position(position);
    }

    private long readCRC32Checksum(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        long crc32Checksum = buf.getLong();
        buf.position(position);
        return crc32Checksum;
    }

    /**
     * getDataLength: Get the length of the data from the buffer. The actual data follows the token and length bytes.
     *
     * @param buf: The buffer
     * @return: The length of the data in the buffer
     */
    private int getDataLength(ByteBuffer buf) {
        buf.position(tokenLength);
        int length = buf.getInt();
        buf.position(buf.position() + checkSumLength); // Leave the position where the data starts.
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
        //       buf.limit(BUFFER_SIZE);
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
                        respondToFrontEnd(newBuf);
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

    private void waitTillDone(final AtomicBoolean done, final int maxMicroseconds) {
        try {
            done.set(false);
            int countDown = maxMicroseconds;
            while (--countDown > 0 && !done.get())
                Thread.sleep(0, 1000);

            logger.log(Level.INFO, "waitTillDone timed out after " + (maxMicroseconds - countDown) + " done = " + done.get());
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception in waitTillDone: " + ex.getClass().getName() + " " + ex.getMessage());
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
