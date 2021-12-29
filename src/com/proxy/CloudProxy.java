package com.proxy;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.proxy.SslUtil.*;

public class CloudProxy implements SslContextProvider {
    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    public static final int BUFFER_SIZE = 16000;
    private static final Logger logger = Logger.getLogger("CloudProxy");
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    SSLSocket cloudChannel;
    private boolean running = false;
    private final String webserverHost;
    private final int webserverPort;
    private final String cloudHost;
    private final int cloudPort;

    private final ExecutorService splitMessagesExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService webserverReadExecutor = Executors.newCachedThreadPool();
    private ExecutorService webserverWriteExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService sendResponseToCloudExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService cloudConnectionCheckExecutor = Executors.newSingleThreadScheduledExecutor();
    private ExecutorService startCloudInputProcessExecutor = Executors.newSingleThreadExecutor();

    CloudProxy(String webServerHost, int webServerPort, String cloudHost, int cloudPort) {
        this.webserverHost = webServerHost;
        this.webserverPort = webServerPort;
        this.cloudHost = cloudHost;
        this.cloudPort = cloudPort;
    }

    public static void main(String[] args) {
        new CloudProxy("192.168.0.31", 8088, "localhost", 8081).start();
    }

    final Object LOCK = new Object();

    void start() {
        running = true;
        try {
            createConnectionToCloud();

            synchronized (LOCK) {
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    showExceptionDetails(e, "start");
                }
            }
        } catch (Exception ex) {
            showExceptionDetails(ex, "start");
        }
    }

    private void createConnectionToCloud() {
        try {
            if (this.cloudChannel == null || !this.cloudChannel.isConnected() || this.cloudChannel.isClosed()) {
                SSLSocket cloudChannel = createSSLSocket(cloudHost, cloudPort, this);
                this.cloudChannel = cloudChannel;
                logger.log(Level.INFO, "Connected successfully to the Cloud");
                startCloudInputProcess(cloudChannel);
            }

        } catch (Exception e) {
            showExceptionDetails(e, "createConnectionToCloud: Couldn't connect to cloud service");
            if (this.cloudChannel != null) {
                try {
                    this.cloudChannel.close();
                } catch (IOException ignored) {
                }
            }
        }
        startCloudConnectionCheck();
    }

    private void startCloudInputProcess(SSLSocket cloudChannel) {
        final AtomicBoolean busy = new AtomicBoolean(false);
        startCloudInputProcessExecutor.execute(() -> {
            try {
                if (!busy.get()) {
                    busy.set(true);
                    InputStream is = cloudChannel.getInputStream();
                    ByteBuffer buf = getBuffer();
                    while (read(is, buf) != -1) {
                        splitMessages(buf);
                        buf = getBuffer();
                    }
                    recycle(buf);
                    busy.set(false);
                }
            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudInputProcess");
                restart();
            }
        });
    }

    private void startCloudConnectionCheck() {
        try {
            final ByteBuffer buf = getBuffer(-1);
            buf.put("Ignore".getBytes(StandardCharsets.UTF_8));
            setDataLength(buf, buf.position() - headerLength);

            cloudConnectionCheckExecutor.scheduleAtFixedRate(() -> {
                try {
                    OutputStream os = cloudChannel.getOutputStream();
                    if (cloudChannel != null && cloudChannel.isConnected() && !cloudChannel.isClosed()) {
                        setBufferForSend(buf);
                        write(os, buf);  // This will be ignored by the Cloud, just throws an exception if the link is down
                    } else throw new Exception("Not connected");
                } catch (Exception ex) {
                    logger.log(Level.WARNING, "Problem with connection to Cloud: " + ex.getMessage());
                    if (cloudChannel != null && !cloudChannel.isClosed()) {
                        try {
                            cloudChannel.close();
                        } catch (IOException ignored) {
                        }
                    }
                    restart();
                }
            }, 10, 10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            showExceptionDetails(ex, "startCloudConnectionCheck");
            restart();
        }
    }

    void removeSocket(int token) {
        tokenSocketMap.remove(token);
    }

    /**
     * cleanUpForRestart: Some sort of problem occurred with the Cloud connection, ensure we restart cleanly
     */
    private void restart() {
        try {
            sendResponseToCloudExecutor.shutdownNow();
            startCloudInputProcessExecutor.shutdownNow();
            cloudConnectionCheckExecutor.shutdownNow();
            webserverWriteExecutor.shutdownNow();

            sendResponseToCloudExecutor = Executors.newSingleThreadExecutor();
            startCloudInputProcessExecutor = Executors.newSingleThreadExecutor();
            cloudConnectionCheckExecutor = Executors.newSingleThreadScheduledExecutor();
            webserverWriteExecutor = Executors.newSingleThreadExecutor();

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
            if (cloudChannel != null && cloudChannel.isConnected() && !cloudChannel.isClosed()) {
                try {
                    cloudChannel.close();
                } catch (IOException ignored) {
                }
            }
            cloudChannel = null;

            // Restart the start process
            new Thread(this::createConnectionToCloud).start();
        } catch (Exception ex) {
            showExceptionDetails(ex, "restart");
        }
    }

    private void writeRequestToWebserver(ByteBuffer buf) {
        // Dump the connection test heartbeats
        try {
            final String ignored = "Ignore";
            if (getToken(buf) == -1 && getDataLength(buf) == ignored.length()) {
                String strVal = new String(Arrays.copyOfRange(buf.array(), headerLength, buf.limit()), StandardCharsets.UTF_8);
                if (ignored.equals(strVal)) {
                    return;
                }
            }

            int token = getToken(buf);
            if (tokenSocketMap.containsKey(token)) {
                if (getConnectionClosedFlag(buf) != 0) {
                    tokenSocketMap.get(token).close();
                    removeSocket(token);
                } else {
                    SocketChannel webserverChannel = tokenSocketMap.get(token);
                    writeRequestToWebserver(buf, webserverChannel);
                }
            } else  // Make a new connection to the webserver
            {
                final SocketChannel webserverChannel = SocketChannel.open();
                webserverChannel.connect(new InetSocketAddress(webserverHost, webserverPort));
                webserverChannel.configureBlocking(true);
                tokenSocketMap.put(token, webserverChannel);
                writeRequestToWebserver(buf, webserverChannel);
                readResponseFromWebserver(webserverChannel, token);
            }
        } catch (Exception ex) {
            showExceptionDetails(ex, "writeRequestToWebserver");
            restart();
        }
    }

    private void writeRequestToWebserver(final ByteBuffer buf, final SocketChannel webserverChannel) {
        this.webserverWriteExecutor.submit(() -> {
            //  logMessageMetadata(buf, "To webserv");
            try {
                int length = getDataLength(buf);
                buf.position(headerLength);
                buf.limit(headerLength + length);
                int result;
                do {
                    result = webserverChannel.write(buf);
                }
                while (result != -1 && buf.position() < buf.limit());
            } catch (ClosedChannelException ignored) {
                // Don't report AsynchronousCloseException or ClosedChannelException as these come up when the channel
                // has been closed by a signal via getConnectionClosedFlag  from ???CloudProxy???
            } catch (Exception ex) {
                showExceptionDetails(ex, "writeRequestToWebserver");
            }
        });
    }

    private void readResponseFromWebserver(SocketChannel webserverChannel, int token) {
        webserverReadExecutor.submit(() -> {
            try {
                ByteBuffer buf = getBuffer(token);
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
            } catch (Exception e) {
                showExceptionDetails(e, "readResponseFromWebserver");
            }
        });
    }

    private void sendResponseToCloud(ByteBuffer buf) {
        sendResponseToCloudExecutor.submit(() -> {
            boolean retVal = true;

            try {
                OutputStream os = cloudChannel.getOutputStream();
                setBufferForSend(buf);

                int result;
                do {
                    write(os, buf);
                }
                while (buf.position() < buf.limit());
                recycle(buf);
            } catch (Exception ex) {
                showExceptionDetails(ex, "sendResponseToCloud");
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

    void setBufferForSend(ByteBuffer buf) throws Exception {
        buf.flip();
    }

    private void write(OutputStream os, ByteBuffer buf) throws IOException
    {
        os.write(buf.array(), buf.position(), buf.limit()-buf.position());
        os.flush();
        buf.position(buf.limit());
    }

    private int read(InputStream is, ByteBuffer buf) throws IOException {
        final int retVal = is.read(buf.array(), buf.position(),  buf.capacity()-buf.position());
        if(retVal != -1) {
            buf.limit(buf.position() + retVal);
            buf.position(buf.limit());
        }
        return retVal;
    }

    ByteBuffer remainsOfPreviousBuffer = null;

    void splitMessages(ByteBuffer buf) {
        splitMessagesExecutor.submit(() -> {
            try {
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
                            ByteBuffer newBuf = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.position() + lengthThisMessage));
                            newBuf.rewind();
                            //    logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                            combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                            writeRequestToWebserver(newBuf);
                        }
                    }
                }
                recycle(buf);
            } catch (Exception ex) {
                showExceptionDetails(ex, "splitMessages");
                restart();
            }
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

    @Override
    public KeyManager[] getKeyManagers() throws GeneralSecurityException, IOException {
        return createKeyManagers(RestfulProperties.CLOUD_PROXY_KEYSTORE_PATH, RestfulProperties.CLOUD_PROXY_KEYSTORE_PASSWORD.toCharArray());
    }

    @Override
    public String getProtocol() {
        return "TLSv1.2";
    }

    @Override
    public TrustManager[] getTrustManagers() throws GeneralSecurityException, IOException {
        return createTrustManagers(RestfulProperties.TRUSTSTORE_PATH, RestfulProperties.TRUSTSTORE_PASSWORD.toCharArray());
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
