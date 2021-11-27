package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CloudProxy {

    private static final String webServerAddress = "192.168.0.29:443";
    public final int cloudListeningPort;
    public final String cloudHost;
    private final int tokenLength = 36;

    public static final int BUFFER_SIZE = 300;

    private static final Logger logger = Logger.getLogger("CloudProxy");
    private final String webServerHost;
    private final int webServerPort;

    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    final private Queue<AsynchronousSocketChannel> cloudConnectionQueue = new ConcurrentLinkedQueue<>();

    private static abstract class Handler<A> implements CompletionHandler<Integer, A> {
        @Override
        public void failed(Throwable exc, A attachment) {
            error(exc, attachment);
        }
    }

    CloudProxy(String webServerHost, int webServerPort, String cloudHost, int cloudListeningPort) {
        this.webServerHost = webServerHost;
        this.webServerPort = webServerPort;
        this.cloudHost = cloudHost;
        this.cloudListeningPort = cloudListeningPort;
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
            new CloudProxy(host, port, "localhost", 8081).start();
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    void start() {
        try {
            CountDownLatch done = new CountDownLatch(1);
            createConnection();

            done.await();
        } catch (InterruptedException iex) {
            System.out.println("InterruptedException in CloudProxy.start: " + iex.getMessage());
        } catch (Exception ex) {
            System.out.println("IOException in CloudProxy.start: " + ex.getMessage());
        }
    }

    private void createConnection()
    {
        try {
                final AsynchronousSocketChannel cloudChannel = AsynchronousSocketChannel.open();
                cloudChannel.connect(new InetSocketAddress(cloudHost, cloudListeningPort), cloudChannel, new CompletionHandler<Void, AsynchronousSocketChannel>() {
                    @Override
                    public void completed(final Void nothing, AsynchronousSocketChannel client) {
                        final AsynchronousSocketChannel server;
                        try {
                            useCloudConnection(cloudChannel);
                        } catch (Exception e) {
                            error(e, "connect failed: " + webServerAddress);
                            System.exit(1);
                            return;
                        }
                    }

                    @Override
                    public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                        error(exc, "accept");
                        System.exit(1);
                    }
                });

        } catch (Exception e) {
            error(e, "connect failed: " + webServerAddress);
            System.exit(1);
            return;
        }
    }

    void readAndGetToken(AsynchronousSocketChannel cloudChannel, WebServerChannels webServerChannels)
    {
        ByteBuffer buf = getBuffer();

        cloudChannel.read(buf,
                new Attachment(new Entry("webServerChannels",webServerChannels), new Entry("buffer", buf)) ,
                new SetUpWebConnectionHandler());
    }

    private void useCloudConnection(AsynchronousSocketChannel cloudChannel) {
        try {
            final WebServerChannels webServerChannels = new WebServerChannels();

            readAndGetToken(cloudChannel, webServerChannels);

            logger.log(Level.INFO, "CloudProxy.useCloudConnection called");

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception in useConnection: " + ex.getMessage());
        }
    }

    private ByteBuffer getBuffer() {
        ByteBuffer poll = bufferQueue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }
    private ByteBuffer removeToken(ByteBuffer buf) {
        final int guidLength = tokenLength;

        buf.position(guidLength);  // Offset to just after the token
        return buf;
    }

    private String getToken(ByteBuffer buf) {
        int position = buf.position();
        buf.position(0);
        byte[] bytes = new byte[tokenLength];
        buf.get(bytes, 0, tokenLength);
        buf.position(position);
        return new String(bytes);
    }

    static class Entry extends  AbstractMap.SimpleEntry<String, Object>
    {
        Entry(String key, Object value)
        {
            super(key, value);
        }
    }

    static class Attachment extends  LinkedHashMap<String, Object> {
        Attachment(Entry ...entry)
        {
            for(Entry a: entry)
            {
                this.put(a.getKey(), a.getValue());
            }
        }
    }

    static final class WebServerChannels extends LinkedHashMap<String, AsynchronousSocketChannel> {}

    final class SetUpWebConnectionHandler implements CompletionHandler<Integer, Attachment> {
        @Override
        public void completed(Integer result, Attachment attachment) {
            WebServerChannels wsChannels = (WebServerChannels)attachment.get("webserverChannels");
            ByteBuffer buf = (ByteBuffer)attachment.get("buffer");
            if(result >= tokenLength)
            {
                String token = getToken(buf);
                if(wsChannels.containsKey(token))
                {
                }
            }
        }

        @Override
        public void failed(Throwable exc, Attachment attachment) {

        }
    }
}

class Cloud {
    int frontEndFacingPort;
    int clientFacingPort;
    private final int tokenLength = 36;
    public static final int BUFFER_SIZE = 300;
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    AsynchronousSocketChannel clientSocket;

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
                    clientSocket=client;
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
            listenerToFrontEnd.accept(clientSocket, new CompletionHandler<AsynchronousSocketChannel, AsynchronousSocketChannel>() {
                public void completed(final AsynchronousSocketChannel frontEnd, AsynchronousSocketChannel clientSocket) {
                    // accept the next connection
                    listenerToFrontEnd.accept(null, this);
                    logger.log(Level.INFO, "Cloud.start accepted connection");
                    if (clientSocket != null) {
                        ByteBuffer buf = getByteBufferWithToken();
                        transfer(frontEnd, clientSocket, buf);
                        transfer(clientSocket, frontEnd, buf);
                    } else
                        logger.log(Level.SEVERE, "No available connections to the ClientProxy");
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel attachment) {
                    System.out.println("Exception in Cloud.start.listener.accept: " + exc.getMessage());
                }

                private void transfer(final AsynchronousSocketChannel reader, AsynchronousSocketChannel writer,  ByteBuffer buffer) {
                     reader.read(buffer, writer, new Cloud.Handler<AsynchronousSocketChannel>() {
                        @Override
                        public void completed(Integer result, AsynchronousSocketChannel writer) {
                            logger.log(Level.INFO, "Cloud.read " + result + " bytes");

                            if (result == -1) {
                                logger.log(Level.INFO, "Cloud.read completed with -1, exiting");
                                return;
                            }
                            writer.write(buffer.flip(), buffer, new Cloud.Handler<ByteBuffer>() {
                                @Override
                                public void completed(Integer result, ByteBuffer attachment) {
                                    logger.log(Level.INFO, "Cloud.read writer.write completed with " + result + " bytes");

                                    //bufferQueue.add(buffer.clear());
                                }
                            });
                            if (reader.isOpen()) {
                                logger.log(Level.INFO, "Cloud.read, reading again");
                                buffer.position(tokenLength);
                                transfer(reader, writer, buffer);
                            }
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


    private ByteBuffer getByteBufferWithToken() {
        ByteBuffer buf = getBuffer();

        UUID uuid = UUID.randomUUID();
        buf.put(uuid.toString().getBytes());
        return buf;
    }
}
