package com.proxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
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
    SocketChannel cloudChannel;
    private boolean running = false;
    private final String webserverHost;
    private final int webserverPort;
    private final String cloudHost;
    private final int cloudPort;

    private final int threadPoolSize = 15;
    private ExecutorService splitMessagesExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService webserverReadExecutor = Executors.newFixedThreadPool(threadPoolSize);
    private ExecutorService webserverWriteExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService sendResponseToCloudExecutor = Executors.newSingleThreadExecutor();

    CloudProxy(String webServerHost, int webServerPort, String cloudHost, int cloudPort) {
        this.webserverHost = webServerHost;
        this.webserverPort = webServerPort;
        this.cloudHost = cloudHost;
        this.cloudPort = cloudPort;
    }

    public static void main(String[] args) {
        new CloudProxy("192.168.0.29", 443, "192.168.0.37", 8081).start();
    }

    final Object LOCK = new Object();

    void start() {
        running=true;
        try {
            createConnectionToCloud();

            synchronized (LOCK) {
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    showExceptionDetails(e, "start");
                }
            }
        } catch(Exception ex) {
            showExceptionDetails(ex, "start");
        }
    }

    private void createConnectionToCloud() {
        try {

            if (this.cloudChannel == null || !this.cloudChannel.isConnected() || !this.cloudChannel.isOpen()) {
                SocketChannel cloudChannel = SocketChannel.open();
                cloudChannel.connect(new InetSocketAddress(cloudHost, cloudPort));
                cloudChannel.configureBlocking(true);
                this.cloudChannel = cloudChannel;
                logger.log(Level.INFO, "Connected successfully to the Cloud");
                startCloudInputProcess(cloudChannel);
                startCloudConnectionCheck();
            }

        } catch (IOException e) {
            logger.log(Level.WARNING, "Couldn't connect to cloud service");
        }
    }

    private void startCloudInputProcess(SocketChannel cloudChannel) {
        final AtomicBoolean busy = new AtomicBoolean(false);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.execute(() -> {
            if (!busy.get()) {
                busy.set(true);
                ByteBuffer buf = getBuffer();
                try {
                    while (cloudChannel.read(buf) != -1) {
                        splitMessages(buf);
                        buf = getBuffer();
                    }
                } catch (Exception ex) {
                    showExceptionDetails(ex, "startCloudProxyInputProcess");
                    executor.shutdown();
                    restart();
                }
                recycle(buf);
                busy.set(false);
            }
        });
        executor.shutdown();
    }

    private void startCloudConnectionCheck()
    {
        final ByteBuffer buf = getBuffer(-1);
        buf.put("Ignore".getBytes(StandardCharsets.UTF_8));
        setDataLength(buf, buf.position()-headerLength);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            try {
                if(cloudChannel != null && cloudChannel.isConnected() && cloudChannel.isOpen()) {
                    setBufferForSend(buf);
                    cloudChannel.write(buf);  // This will be ignored by the Cloud, just throws an exception if the link is down
                }
                else throw new Exception("Not connected");
            }
            catch(Exception ex)
            {
                logger.log(Level.WARNING, "Problem with connection to Cloud: "+ex.getMessage());
                restart();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    void removeSocket(int token) {
        tokenSocketMap.remove(token);
    }

    /**
     * cleanUpForRestart: Some sort of problem occurred with the Cloud connection, ensure we restart cleanly
     */
    private void restart() {
        running = false;
        synchronized (LOCK)
        {
            LOCK.notify(); // End the current start process
        }
        splitMessagesExecutor.shutdownNow();
        webserverReadExecutor.shutdownNow();
        webserverWriteExecutor.shutdownNow();
        sendResponseToCloudExecutor.shutdownNow();

        splitMessagesExecutor = Executors.newSingleThreadExecutor();
        webserverReadExecutor = Executors.newFixedThreadPool(threadPoolSize);
        webserverWriteExecutor = Executors.newSingleThreadExecutor();
        sendResponseToCloudExecutor = Executors.newSingleThreadExecutor();

        // Ensure all sockets in the token/socket map are closed
        tokenSocketMap.forEach((token, socket) -> {
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        });
        // Clear the token/socket map
        tokenSocketMap.clear();
        remainsOfPreviousBuffer = null;
        // Ensure the connection is actually closed
        if (cloudChannel != null && cloudChannel.isConnected() && cloudChannel.isOpen()) {
            try {
                cloudChannel.close();
            } catch (IOException ignored) {
            }
        }
        // Restart the start process
        new Thread(this::start).start();
     }

    private void writeRequestToWebserver(ByteBuffer buf) {
        int token = getToken(buf);
        if (tokenSocketMap.containsKey(token)) {
            if (getConnectionClosedFlag(buf) != 0) {
                try {
                    tokenSocketMap.get(token).close();
                    removeSocket(token);
                } catch (IOException e) {
                    showExceptionDetails(e, "closing webserverChannel in writeRequestToWebserver");
                }
            } else {
                SocketChannel webserverChannel = tokenSocketMap.get(token);
                writeRequestToWebserver(buf, webserverChannel);
            }
        } else  // Make a new connection to the webserver
        {
            try {
                final SocketChannel webserverChannel = SocketChannel.open();
                webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort));
                webserverChannel.configureBlocking(true);
                tokenSocketMap.put(token, webserverChannel);
                writeRequestToWebserver(buf, webserverChannel);
                readResponseFromWebserver(webserverChannel, token);
            } catch (IOException ioex) {
                showExceptionDetails(ioex, "writeRequestsToWebserver");
            }
        }
    }

    private void writeRequestToWebserver(final ByteBuffer buf, final SocketChannel webserverChannel) {
        this.webserverWriteExecutor.submit(() -> {  // submit rather than execute to ensure tasks (and hence messages) are run in order of submission
          //  logMessageMetadata(buf, "To webserv");
            int length = getDataLength(buf);
            buf.position(headerLength);
            buf.limit(headerLength + length);
            int result;
            try {
                do {
                    result = webserverChannel.write(buf);
                }
                while (result != -1 && buf.position() < buf.limit());
            } catch (ClosedChannelException ignored) {
                // Don't report AsynchronousCloseException or ClosedChannelException as these come up when the channel
                // has been closed by a signal via getConnectionClosedFlag  from CloudProxy
            } catch (IOException e) {
                showExceptionDetails(e, "writeRequestToWebserver");
            }
        });
    }

    private void readResponseFromWebserver(SocketChannel webserverChannel, int token) {
        webserverReadExecutor.submit(() -> {
            ByteBuffer buf = getBuffer(token);
            try {
                while (running && webserverChannel.isOpen() && webserverChannel.read(buf) != -1) {
                    setDataLength(buf, buf.position() - headerLength);
                    sendResponseToCloud(buf);
                    buf = getBuffer(token);
                }
                setConnectionClosedFlag(buf);
                sendResponseToCloud(buf);
            } catch (AsynchronousCloseException ignored) {
                // Don't report AsynchronousCloseException as these come up when the channel has been closed
                //  by a signal via getConnectionClosedFlag  from Cloud
            } catch (IOException e) {
                showExceptionDetails(e, "readResponseFromWebserver");
            }
        });
    }

    private void sendResponseToCloud(ByteBuffer buf) {
        sendResponseToCloudExecutor.submit(()->{
            boolean retVal = true;

//            logMessageMetadata(buf, "To cloud  ");
            setBufferForSend(buf);
            try {
                int result;
                do {
                    result = cloudChannel.write(buf);
                }
                while (result != -1 && buf.position() < buf.limit());
                recycle(buf);
            } catch (Exception ex) {
                showExceptionDetails(ex, "sendResponseToCloud");
                restart();
                retVal = false;
            }
            return retVal;
        });
    }

    private int count = 0;
    private int lengthTotal = 0;
    private long checksumTotal = 0;

    private void logMessageMetadata(ByteBuffer buf, String title) {
        int position = buf.position();
        lengthTotal += getDataLength(buf);
        long checksum = getCRC32Checksum(buf);
        checksumTotal += checksum;
        boolean disconnect = getConnectionClosedFlag(buf) != 0;
        System.out.println(title + (disconnect ? "*" : ".") + ".   #: " + ++count + ", Token: " + getToken(buf) + ", Length: " + getDataLength(buf) + ", lengthTotal: " + lengthTotal + ", Checksum: " + checksum + ", ChecksumTotal: " + checksumTotal);
        buf.position(position);
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

    private void setConnectionClosedFlag(ByteBuffer buf) {
        buf.position(tokenLength + lengthLength);
        buf.put((byte) 1);
        setDataLength(buf, 0);
        buf.limit(headerLength);
    }

    private byte getConnectionClosedFlag(ByteBuffer buf) {
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
        int length = getDataLength(buf);
        Checksum crc32 = new CRC32();
        crc32.update(buf.array(), 0, length + headerLength);
        return crc32.getValue();
    }

    void setBufferForSend(ByteBuffer buf) {
        buf.flip();
    }

    ByteBuffer remainsOfPreviousBuffer = null;

    void splitMessages(ByteBuffer buf) {
        splitMessagesExecutor.submit(() -> {
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
                            //    logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                            combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                            writeRequestToWebserver(newBuf);
                        } catch (Exception ex) {
                            showExceptionDetails(ex, "splitMessages");
                        }
                    }
                }
            }
            recycle(buf);
        });
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
    }

    void showExceptionDetails(Throwable t, String functionName) {
        logger.log(Level.SEVERE, t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage());
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
