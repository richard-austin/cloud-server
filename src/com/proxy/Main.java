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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static String serverAddress = "192.168.0.29:443";
    private static ExecutorService es = Executors.newCachedThreadPool();
    public static final int port = 3575;

    public static final int BUFFER_SIZE = 1024;

    private static final Logger logger = Logger.getLogger("Proxy");

    private static abstract class Handler<A> implements CompletionHandler<Integer, A> {
        @Override
        public void failed(Throwable exc, A attachment) {
            error(exc, attachment);
        }
    }

    private static void error(Throwable exc, Object attachment) {
        logger.log(Level.WARNING, "IO failure in " + attachment, exc);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final String host;
        final int port;
        try {
            String[] split = serverAddress.split(":");
            if (split.length != 2) {
                throw new IllegalArgumentException("host:port");
            }
            host = split[0];
            port = Integer.parseInt(split[1]);
        } catch (IllegalArgumentException e) {
            System.exit(1);
            return;
        }

        CountDownLatch done = new CountDownLatch(1);

        AsynchronousServerSocketChannel open = AsynchronousServerSocketChannel.open();
        final AsynchronousServerSocketChannel listener =
                open.bind(new InetSocketAddress(Main.port));
        final Queue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();

        listener.accept(null, new CompletionHandler<AsynchronousSocketChannel,Void>() {
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

            private ByteBuffer getBuffer() {
                ByteBuffer poll = queue.poll();
                return Objects.requireNonNullElseGet(poll, () -> ByteBuffer.allocate(BUFFER_SIZE));
            }

            private void read(final AsynchronousSocketChannel reader, AsynchronousSocketChannel writer) {
                final ByteBuffer buffer = getBuffer();
                reader.read(buffer, writer, new Handler<AsynchronousSocketChannel>() {
                    @Override
                    public void completed(Integer result, AsynchronousSocketChannel writer) {
                        if (result == -1) {
                            return;
                        }
                        writer.write(buffer.flip(), buffer, new Handler<ByteBuffer>() {
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
}
