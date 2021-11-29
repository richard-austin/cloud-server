package com.proxy;

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
import java.util.logging.Level;
import java.util.logging.Logger;

class Cloud {
    int frontEndFacingPort;
    int clientFacingPort;
    private final int tokenLength = 36;
    public static final int BUFFER_SIZE = 300;
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    AsynchronousSocketChannel clientSocket;

    final Queue<ByteBuffer> outQueue = new ConcurrentLinkedQueue<>();
    final Queue<ByteBuffer> inQueue = new ConcurrentLinkedQueue<>();
    final Map<String, AsynchronousSocketChannel> tokenSocketMap = new LinkedHashMap<>();

    private static final Logger logger = Logger.getLogger("Cloud");

    private static abstract class Handler<A> implements CompletionHandler<Integer, A> {
        @Override
        public void failed(Throwable exc, A attachment) {
            error(exc, attachment);
        }
    }

    public static void main(String[] args) {
        new Cloud(8082, 8081).start();
    }

    Cloud(int frontEndFacingPort, int clientFacingPort) {
        this.frontEndFacingPort = frontEndFacingPort;
        this.clientFacingPort = clientFacingPort;

    }

    static final Object LOCK = new Object();

    void start() {
        openServerSocketForCloudProxy();
        acceptConnectionsFromFrontEnd();
        startWriteToCloudProxy();
        startReadFromCloudProxy();
        startRespondToFrontEnd();
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
                    logger.log(Level.SEVERE, "Exception in Cloud.start.listener.accept: " + exc.getMessage());
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
                    logger.log(Level.INFO, "Cloud.start accepted connection");

                    if (frontEnd != null) {
                        String token = getToken();
                        tokenSocketMap.put(token, frontEnd);
                        readFromFrontEnd(frontEnd, token);
                    } else
                        logger.log(Level.SEVERE, "accept socket was null");
                }

                @Override
                public void failed(Throwable exc, Void nothing) {
                    System.out.println("Exception in Cloud.start.listener.accept: " + exc.getMessage());
                }
            });
        } catch (IOException ioex) {
            logger.log(Level.INFO, "IOException in openServerSocketForFrontEnd: " + ioex.getClass().getName() + " " + ioex.getMessage());
        }
    }

    final void readFromFrontEnd(AsynchronousSocketChannel channel, final String token) {
        ByteBuffer buf = getBuffer(token);
        channel.read(buf, null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result != -1) {
                    setDataLength(buf, result);

                    // Fill any remaining space with 0s so that "packets" are all the same size
                    for (int i = result + tokenLength + Integer.BYTES; i < BUFFER_SIZE; ++i)
                        buf.put((byte) 0);

                    outQueue.add(buf);
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

    void startWriteToCloudProxy() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            ByteBuffer buf;
            try {
                while ((buf = outQueue.poll()) != null && clientSocket != null) {
                    clientSocket.write(buf.flip(), null, new CompletionHandler<Integer, Object>() {
                        @Override
                        public void completed(Integer result, Object attachment) {
                            // Nothing more to do for now
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
        }, 300, 10, TimeUnit.MILLISECONDS);
    }

    void startReadFromCloudProxy() {
        AtomicBoolean runAgain = new AtomicBoolean(false);
        readFromCloudProxy(runAgain);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (runAgain.get()) {
                runAgain.set(false);
                readFromCloudProxy(runAgain);
            }
        }, 300, 10, TimeUnit.MILLISECONDS);
    }

    void readFromCloudProxy(AtomicBoolean runAgain) {
        if (clientSocket != null && clientSocket.isOpen()) {
            ByteBuffer buf = getBuffer();
            clientSocket.read(buf, null, new CompletionHandler<Integer, AtomicBoolean>() {
                @Override
                public void completed(Integer result, AtomicBoolean doAgain) {
                    if (result != -1) {
                        inQueue.add(buf);
                        readFromCloudProxy(runAgain);
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

    void startRespondToFrontEnd() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::respondToFrontEnd, 300, 10, TimeUnit.MILLISECONDS);
    }

    void respondToFrontEnd() {
        while (!inQueue.isEmpty()) {
            ByteBuffer buf = inQueue.poll();
            String token = getToken(buf);
            int length = getDataLength(buf);
            AsynchronousSocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
            buf.limit(tokenLength + Integer.BYTES + length);
            buf.flip();
            frontEndChannel.write(buf, null, new CompletionHandler<Integer, Object>() {
                @Override
                public void completed(Integer result, Object attachment) {
                    // Done, nothing more to do
                }

                @Override
                public void failed(Throwable exc, Object attachment) {
                    logger.log(Level.INFO, "startRespondToFrontEnd failed: " + exc.getClass().getName() + " : " + exc.getMessage());
                }
            });
        }
    }

    /**
     * getBuffer: Get a new ByteBuffer of BUFFER_SIZE bytes length.
     * @return: The buffer
     */
    private ByteBuffer getBuffer() {
        ByteBuffer poll = bufferQueue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }

    /**
     * getBuffer: Get a buffer and place the token at the start. Reserve a further Integer.BYTES bytes to contain the length.
     * @param token: The token
     * @return: The byte buffer with the token in place and length reservation set up.
     */
    private ByteBuffer getBuffer(String token) {
        ByteBuffer buf = getBuffer();

        buf.put(token.getBytes());
        buf.putInt(0);  // Reserve space for the data length
        return buf;
    }

    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
    }

    /**
     * setDataLength: Set the Integer.BYTES bytes following the token to the length of the data in the buffer
     *                (minus token and length bytes).
     * @param buf: The buffer to set the length in.
     * @param length: The length to set.
     */
    private void setDataLength(ByteBuffer buf, int length) {
        buf.putInt(tokenLength, length);
    }

    /**
     *getDataLength: Get the length of the data from the buffer. The actual data follows the token and length bytes.
     * @param buf: The buffer
     * @return: The length of the data in the buffer
     */
    private int getDataLength(ByteBuffer buf) {
        int length = buf.getInt(tokenLength);
        buf.position(tokenLength + Integer.BYTES);
        return length;
    }

    /**
     * getToken: Get a unique GUID as a token
     * @return: The token as a string
     */
    private String getToken()
    {
        UUID uuid = UUID.randomUUID();
        return uuid.toString();
    }

    /**
     * getToken: Get the token in the ByteBuffer
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
}
