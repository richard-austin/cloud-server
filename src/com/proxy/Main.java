package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final String serverAddress = "192.168.0.29:443";
 //   private static ExecutorService es = Executors.newCachedThreadPool();
    public final int listeningPort = 3575;

    public static final int BUFFER_SIZE = 1024;

    private static final Logger logger = Logger.getLogger("Proxy");

    final Queue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

    private static abstract class Handler<A> implements CompletionHandler<Integer, A> {
        @Override
        public void failed(Throwable exc, A attachment) {
            error(exc, attachment);
        }
    }

    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
    }

    public static void main(String[] args) {
        final String host;
        final int port;
        try {
            String[] split = serverAddress.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("host:port");
            }
            host = split[0];
            port = Integer.parseInt(split[1]);
            new Main().start(host, port);
        } catch (IllegalArgumentException e) {
            System.exit(1);
        }
    }

    void start(String host, int port)
    {
        try {
            CountDownLatch done = new CountDownLatch(1);

            AsynchronousServerSocketChannel open = AsynchronousServerSocketChannel.open();
            final AsynchronousServerSocketChannel listener =
                    open.bind(new InetSocketAddress(listeningPort));

            listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                public void completed(final AsynchronousSocketChannel client, Void att) {
                    // accept the next connection
                    listener.accept(null, this);

                    final AsynchronousSocketChannel server;
                    try {
                        server = AsynchronousSocketChannel.open();
                        server.connect(new InetSocketAddress(host, port)).get();
                    } catch (Exception e) {
                        error(e, "connect failed: " + serverAddress);
                        System.exit(1);
                        return;
                    }

                    read(client, server);
                    read(server, client);
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    error(exc, "accept");
                    System.exit(1);
                }

                private void read(final AsynchronousSocketChannel reader, AsynchronousSocketChannel writer) {
                    final ByteBuffer buffer = getByteBufferWithToken();
                    reader.read(buffer, writer, new Handler<AsynchronousSocketChannel>() {
                        @Override
                        public void completed(Integer result, AsynchronousSocketChannel writer) {
                            if (result == -1) {
                                return;
                            }
                            ByteBuffer buf = removeToken(buffer.flip());
                            writer.write(buf, buf, new Handler<ByteBuffer>() {
                                @Override
                                public void completed(Integer result, ByteBuffer attachment) {
                                    queue.add(buffer.clear());
                                }
                            });
                            read(reader, writer);
                        }
                    });
                }
            });

            done.await();
        }
        catch(IOException ex)
        {
            System.out.println("IOException in Main.stsrt: "+ex.getMessage());
        }
        catch(InterruptedException iex)
        {
            System.out.println("InterruptedException in Main.stsrt: "+iex.getMessage());
        }
    }

    private ByteBuffer getBuffer() {
        ByteBuffer poll = queue.poll();
        return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
    }

    private ByteBuffer getByteBufferWithToken()
    {
        ByteBuffer buf = getBuffer();

        UUID uuid = UUID.randomUUID();
        buf.put(uuid.toString().getBytes());
        return buf;
    }

    private ByteBuffer removeToken(ByteBuffer buf)
    {
        final int guidLength = 36;

        buf.position(guidLength);  // Offset to just after the token
        return buf;
    }

    private String getToken(ByteBuffer buf)
    {
        int position = buf.position();
        buf.position(0);
        byte[] bytes = new byte[36];
        buf.get(bytes, 0, 36);
        buf.position(position);
        return new String(bytes);
    }
}
