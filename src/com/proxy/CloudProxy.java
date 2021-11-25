package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudProxy {

    private static final String webServerAddress = "192.168.0.29:443";
    //   private static ExecutorService es = Executors.newCachedThreadPool();
    public final int cloudListeningPort = 8081;
    public final String cloudHost = "localhost";

    public static final int BUFFER_SIZE = 1024;

    private static final Logger logger = Logger.getLogger("CloudProxy");
    private final String webServerHost;
    private final int webServerPort;

    final Queue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
    final private Queue<AsynchronousSocketChannel> cloudConnectionQueue = new ConcurrentLinkedQueue<>();

    private static abstract class Handler<A> implements CompletionHandler<Integer, A> {
        @Override
        public void failed(Throwable exc, A attachment) {
            error(exc, attachment);
        }
    }

    CloudProxy(String webServerHost, int webServerPort)
    {
        this.webServerHost =webServerHost;
        this.webServerPort = webServerPort;
    }
    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
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
            new CloudProxy(host, port).start("localhost", 8081);
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    void start(String cloudHost, int cloudPort) {

        try {
            CountDownLatch done = new CountDownLatch(1);

            try {
                final int numberOfClientConnections = 60;

                for (int i = 0; i < numberOfClientConnections; ++i) {
                    final AsynchronousSocketChannel cloudChannel = AsynchronousSocketChannel.open();
                    cloudChannel.connect(new InetSocketAddress(cloudHost, cloudPort), cloudChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                        @Override
                        public void completed(final Void nothing, AsynchronousSocketChannel client) {
                            final AsynchronousSocketChannel server;
                            try {
                                useConnection(cloudChannel);

                                //cloudConnectionQueue.add(cloudChannel);
//                            server = AsynchronousSocketChannel.open();
//                            server.connect(new InetSocketAddress(webServerHost, webServerPort)).get();
                            } catch (Exception e) {
                                error(e, "connect failed: " + webServerAddress);
                                System.exit(1);
                                return;
                            }
//                        read(client, server);
//                        read(server, client);
                        }

                        @Override
                        public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                            error(exc, "accept");
                            System.exit(1);
                        }

                    });
                }
            } catch (Exception e) {
                error(e, "connect failed: " + webServerAddress);
                System.exit(1);
                return;
            }

            done.await();
        } catch (InterruptedException iex) {
            System.out.println("InterruptedException in CloudProxy.start: " + iex.getMessage());
        } catch (Exception ex) {
            System.out.println("IOException in CloudProxy.start: " + ex.getMessage());
        }
    }
    private void read(final AsynchronousSocketChannel reader, AsynchronousSocketChannel writer) {
        final ByteBuffer buffer = getBuffer();
        reader.read(buffer, writer, new CloudProxy.Handler<AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel writer) {
                logger.log(Level.WARNING, new String(buffer.array()));
                if (result == -1) {
                    return;
                }
                writer.write(buffer.flip(), buffer, new CloudProxy.Handler<ByteBuffer>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        queue.add(buffer.clear());
                    }
                });
                read(reader, writer);
            }
        });
    }

    private void useConnection(AsynchronousSocketChannel cloudChannel) {
        try {
        final AsynchronousSocketChannel webServerChannel = AsynchronousSocketChannel.open();
            webServerChannel.connect(new InetSocketAddress(webServerHost, webServerPort), webServerChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                @Override
                public void completed(Void result, AsynchronousSocketChannel attachment) {
                    read(cloudChannel, webServerChannel);
                    read(webServerChannel, cloudChannel);
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel attachment) {

                }
            });

        }
        catch(Exception ex)
        {

        }
       // ByteBuffer buf = getBuffer();

//
//        cloudChannel.read(buf, cloudChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
//            @Override
//            public void completed(Integer result, AsynchronousSocketChannel attachment) {
//                buf.flip();
//                logger.log(Level.FINE, new String(buf.array()));
//                ByteBuffer b = buf;
//                int x = result;
//
//            }
//
//            @Override
//            public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
//                error(exc, "accept");
//                System.exit(1);
//            }
//
//        });
    }

    private ByteBuffer getBuffer() {
        ByteBuffer poll = queue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }
}

class Cloud {
    int frontEndFacingPort;
    int clientFacingPort;
    public static final int BUFFER_SIZE = 1024;
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    final Queue<AsynchronousSocketChannel> clientSocketQueue = new ConcurrentLinkedQueue<>();

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

    void start() {
        try {
            AsynchronousServerSocketChannel openToClientProxy = AsynchronousServerSocketChannel.open();
            final AsynchronousServerSocketChannel listenerToClientProxy =
                    openToClientProxy.bind(new InetSocketAddress(clientFacingPort));

            // Open up a listening port for the ClientProxy on the client machine to connect to
            // The client proxy should make a few connections
            listenerToClientProxy.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel client, Void att) {
                    // accept the next connection
                    listenerToClientProxy.accept(null, this);
                    clientSocketQueue.add(client);  // Pick up the connection and save it for later
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    logger.log(Level.SEVERE, "Exception in Cloud.start.listener.accept: " + exc.getMessage());
                }
            });

            AsynchronousServerSocketChannel openToFrontEnd = AsynchronousServerSocketChannel.open();
            final AsynchronousServerSocketChannel listenerToFrontEnd =
                    openToFrontEnd.bind(new InetSocketAddress(frontEndFacingPort));
            // Open up a listening port for the front end to connect to
            listenerToFrontEnd.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel frontEnd, Void att) {
                    // accept the next connection
                    listenerToFrontEnd.accept(null, this);

                    AsynchronousSocketChannel clientSocket = clientSocketQueue.poll();
                    if (clientSocket != null) {
                        read(frontEnd, clientSocket);
                        read(clientSocket, frontEnd);
                    } else
                        logger.log(Level.SEVERE, "No available connections to the ClientProxy");
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    System.out.println("Exception in Cloud.start.listener.accept: " + exc.getMessage());
                }

                private void read(final AsynchronousSocketChannel reader, AsynchronousSocketChannel writer) {
                    final ByteBuffer buffer = getBuffer();
                    reader.read(buffer, writer, new Cloud.Handler<AsynchronousSocketChannel>() {
                        @Override
                        public void completed(Integer result, AsynchronousSocketChannel writer) {
                            if (result == -1) {
                                try {
                                    writer.close();  // Tell the ClientProxy the connection is finished
                                } catch (IOException ioex) {
                                    logger.log(Level.SEVERE, "Exception shutting down input on clientSocket: " + ioex.getMessage());
                                }
                                return;
                            }
                            writer.write(buffer.flip(), buffer, new Cloud.Handler<ByteBuffer>() {
                                @Override
                                public void completed(Integer result, ByteBuffer attachment) {
                                    bufferQueue.add(buffer.clear());
                                }
                            });
                            read(reader, writer);
                        }
                    });
                }
            });
            try {
                for (; ; )
                    Thread.sleep(100);
            } catch (InterruptedException iex) {
                logger.log(Level.WARNING, "Process interrupted: " + iex.getMessage());
            }
        } catch (IOException ioex) {
            logger.log(Level.SEVERE, "IOException in Cloud.start: " + ioex.getMessage());
        }
    }

    private ByteBuffer getBuffer() {
        ByteBuffer poll = bufferQueue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }

    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
    }
}


//    private ByteBuffer getByteBufferWithToken()
//    {
//        ByteBuffer buf = getBuffer();
//
//        UUID uuid = UUID.randomUUID();
//        buf.put(uuid.toString().getBytes());
//        return buf;
//    }
//
//    private ByteBuffer removeToken(ByteBuffer buf)
//    {
//        final int guidLength = 36;
//
//        buf.position(guidLength);  // Offset to just after the token
//        return buf;
//    }
//
//    private String getToken(ByteBuffer buf)
//    {
//        int position = buf.position();
//        buf.position(0);
//        byte[] bytes = new byte[36];
//        buf.get(bytes, 0, 36);
//        buf.position(position);
//        return new String(bytes);
//    }

