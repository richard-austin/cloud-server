/**
 * Proxy: A proxy server, not used in cloud-server
 */
package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Proxy {
    private final int frontEndFacingPort = 8082;
    private static final Logger logger = Logger.getLogger("Proxy");
    private final int bufferSize = 1024;
    private final String webserverHost = "192.168.0.29";
    private final int webserverPort = 443;

//    public static void main(String[] args) {
//        new Proxy().start();
//    }

    private final Object LOCK = new Object();

    void start() {
        acceptConnectionsFromFrontEnd();
        try {
            synchronized (LOCK) {
                LOCK.wait(0, 0);
            }
        } catch (InterruptedException iex) {
            logger.log(Level.WARNING, "Process interrupted: " + iex.getMessage());
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
                    logger.log(Level.INFO, "Proxy.start accepted connection");
                    connectToWebserver(frontEnd);
                }

                @Override
                public void failed(Throwable exc, Void nothing) {
                    System.out.println("Exception in Proxy.start.listener.accept: " + exc.getMessage());
                }
            });
        } catch (IOException ioex) {
            logger.log(Level.INFO, "IOException in openServerSocketForFrontEnd: " + ioex.getClass().getName() + " " + ioex.getMessage());
        }
    }

    void connectToWebserver(final AsynchronousSocketChannel frontEnd) {
        try {
            final AsynchronousSocketChannel webserverChannel = AsynchronousSocketChannel.open();
            webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort), frontEnd, new CompletionHandler<Void, AsynchronousSocketChannel>() {

                @Override
                public void completed(Void result, final AsynchronousSocketChannel frontEnd) {
                    read(frontEnd, webserverChannel);
                    read(webserverChannel, frontEnd);
                }

                @Override
                public void failed(Throwable exc, final AsynchronousSocketChannel frontEnd) {
                }
            });
        } catch (IOException ioex) {
            logger.log(Level.INFO, "writeRequestsToWebserver failed: " + ioex.getClass().getName() + " : " + ioex.getMessage());
        }

    }

    void read(AsynchronousSocketChannel from, AsynchronousSocketChannel to) {
        final ByteBuffer buf = ByteBuffer.allocate(bufferSize);
        from.read(buf, to, new CompletionHandler<Integer, AsynchronousSocketChannel>() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel to) {
                if (result != -1) {
//                    try {
//                        logger.log(Level.INFO, "read from " + from.getRemoteAddress().toString());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
                    write(to, from, buf);
                } else {
                    try {
                        to.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    logger.log(Level.INFO, "read returned " + result);
                }
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel to) {
            }
        });
    }

    void write(AsynchronousSocketChannel to, AsynchronousSocketChannel from, ByteBuffer buf)
    {
        final AtomicInteger writeTotal= new AtomicInteger(0);
        to.write(buf.flip(), null, new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                if (result != -1) {
                    if(writeTotal.addAndGet(result) < buf.limit())
                        write(to, from, buf);
                    else
                        read(from, to);
//                                try {
//                                    logger.log(Level.INFO, "written to " + to.getRemoteAddress().toString());
//                                } catch (IOException e) {
//                                    e.printStackTrace();
//                                }

                } else {
                    try {
                        from.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    logger.log(Level.INFO, "write returned " + result);
                }
            }

            @Override
            public void failed(Throwable exc, Object attachment) {

            }
        });

    }
}
