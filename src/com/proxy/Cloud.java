package com.proxy;

import org.apache.commons.lang3.ArrayUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.proxy.SslUtil.*;

public class Cloud implements SslContextProvider {
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    private boolean running = true;
    private final ExecutorService browserWriteExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService browserReadExecutor = Executors.newCachedThreadPool();
    private final ExecutorService splitMessagesExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService sendToCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService startCloudProxyInputProcessExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService acceptConnectionsFromCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private String JSESSIONID = "";

    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();

    private static final Logger logger = Logger.getLogger("CloudAsync");
    public static final int BUFFER_SIZE = 16000;
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    private SSLSocket cloudProxy;
    private final int browserFacingPort = 8083, cloudProxyFacingPort = 8081;

    public static void main(String[] args) {
        new Cloud().start();
    }

    private void start() {
        acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
        acceptConnectionsFromBrowser(browserFacingPort); // Never returns
    }


    private void acceptConnectionsFromBrowser(final int browserFacingPort) {
        while (running) {
            try {
                // Creating a ServerSocket to listen for connections with
                ServerSocketChannel s = ServerSocketChannel.open();
                s.bind(new InetSocketAddress(browserFacingPort));
                while (running) {
                    try {
                        // It will wait for a connection on the local port
                        SocketChannel browser = s.accept();
                        browser.configureBlocking(true);
                        final int token = getToken();
                        updateSocketMap(browser, token);
                        readFromBrowser(browser, token);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "Exception in acceptConnectionsFromBrowser: " + ex.getClass().getName() + ": " + ex.getMessage());
                    }
                }
            } catch (IOException ioex) {
                logger.log(Level.SEVERE, "IOException in acceptConnectionsFromBrowser: " + ioex.getClass().getName() + ": " + ioex.getMessage());
            }
        }
    }

    private void acceptConnectionsFromCloudProxy(final int cloudProxyFacingPort) {
        acceptConnectionsFromCloudProxyExecutor.execute(() -> {
            while (running) {
                try {
                    // Creating a ServerSocket to listen for connections with
                    SSLServerSocket s = createSSLServerSocket(cloudProxyFacingPort, this);
                    while (running) {
                        try {
                            // It will wait for a connection on the local port
                            SSLSocket cloudProxy = (SSLSocket) s.accept();
                            cloudProxy.setSoTimeout(0);
                            cloudProxy.setSoLinger(true, 5);

                            this.cloudProxy = cloudProxy;
                            remainsOfPreviousBuffer = null;
                            clearSocketMap();
                            authenticate(cloudProxy);
                            startCloudProxyInputProcess();
                        } catch (IOException ioex) {
                            logger.severe("IOException in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                            reset();
                        }
                    }
                } catch (Exception ioex) {
                    logger.severe("Exception in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                    reset();
                }
            }
        });
    }

    private void startCloudProxyInputProcess() {
        startCloudProxyInputProcessExecutor.scheduleAtFixedRate(() -> {
            try {
                if (cloudProxy != null && !cloudProxy.isClosed()) {
                    ByteBuffer buf = getBuffer();
                    buf.flip();
                    InputStream is = cloudProxy.getInputStream();
                    while (read(is, buf) != -1) {
                        splitMessages(buf);
                        buf = getBuffer();
                    }
                    recycle(buf);
                }
            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudProxyInputProcess");
                reset();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void sendResponseToCloudProxy(ByteBuffer buf) {
        sendToCloudProxyExecutor.submit(() -> {
            try {
                OutputStream os = cloudProxy.getOutputStream();
                do {
                    write(os, buf);
                }
                while (buf.position() < buf.limit());
                recycle(buf);

            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudProxyOutputProcess");
                reset();
            }
        });
    }

    String authenticate(SSLSocket socket) {
        try {
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            ByteBuffer buf = getBuffer(getToken());
            //socket.setReceiveBufferSize(buf.length);

            String payload = "username=" + RestfulProperties.USERNAME + "&password=" + RestfulProperties.PASSWORD;

            String output = "POST /login/authenticate HTTP/1.1\r\n" +
                    "Host: host\r\n" +
                    "DNT: 1\r\n" +
                    "Upgrade-Insecure-Requests: 1\r\n" +
                    "Content-type: application/x-www-form-urlencoded\r\n" +
                    "Content-Length: " + payload.length() + "\r\n\r\n" +
                    payload + "\r\n";

            setDataLength(buf, output.length());
            buf.put(output.getBytes());
            setBufferForSend(buf);
            write(os, buf);
            System.out.print(output);
            buf.clear();
            if (read(is, buf) != -1) {
                HttpMessage hdrs = new HttpMessage(buf.array(), headerLength);
                var l = hdrs.getHeader("Location");
                String location = l.size() == 1 ? l.get(0) : null;
                if (Objects.equals(location, "/")) {
                    List<String> setCookie = hdrs.getHeader("Set-Cookie");
                    for (String cookie : setCookie) {
                        if (cookie.startsWith("JSESSIONID")) {
                            JSESSIONID = cookie.substring(11, 43);
                            break;
                        } else
                            JSESSIONID = "";
                    }
                }
            }
            String response = new String(buf.array());
            System.out.print(response);

        } catch (Exception ex) {
            System.out.println("Exception in authenticate: " + ex.getMessage());
        }

        return JSESSIONID;
    }

    final private AtomicReference<byte[]> lastBitOfPreviousBuffer = new AtomicReference<>(null);

    final void readFromBrowser(SocketChannel channel, final int token) {
        browserReadExecutor.submit(() -> {
            int result;
            ByteBuffer buf = getBuffer();
            try {
                while ((result = channel.read(buf)) != -1) {
                    splitHttpMessages(buf.array(), result, cloudProxy.getOutputStream(), token, lastBitOfPreviousBuffer);
                    buf = getBuffer();
                }
                setConnectionClosedFlag(buf);
                // removeSocket(token);
                sendResponseToCloudProxy(buf);
            } catch (IOException ignored) {
                // setConnectionClosedFlag(buf);
                removeSocket(token);
                // sendResponseToCloudProxy(buf);
                reset();
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                reset();
            }
        });
    }

    private void respondToBrowser(ByteBuffer buf) {
        // Dump the connection test heartbeats
        final String ignored = "Ignore";
        if (getToken(buf) == -1 && getDataLength(buf) == ignored.length()) {
            String strVal = new String(Arrays.copyOfRange(buf.array(), headerLength, buf.limit()), StandardCharsets.UTF_8);
            if (ignored.equals(strVal)) {
                return;
            }
        }

        browserWriteExecutor.submit(() -> {
            try {
//            logMessageMetadata(buf, "To browser");
                int token = getToken(buf);
                int length = getDataLength(buf);
                SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                if (getConnectionClosedFlag(buf) != 0) {
                    removeSocket(token);  // Usually already gone
                } else if (frontEndChannel == null)
                    throw new Exception("Couldn't find a socket for token " + token);
                else if (frontEndChannel.isOpen()) {
                    buf.position(headerLength);
                    buf.limit(headerLength + length);
                    int result;
                    try {
                        do {
                            result = frontEndChannel.write(buf);
                        }
                        while (result != -1 && buf.position() < buf.limit());
                    } catch (IOException ioex) {
                        logger.severe("IOException in respondToBrowser: " + ioex.getMessage());
                        setConnectionClosedFlag(buf);
                        sendResponseToCloudProxy(buf);
                        frontEndChannel.shutdownOutput().shutdownOutput().close();
                    }
                } else
                    logger.severe("Socket for token " + token + " was closed");
            } catch (Exception ex) {
                showExceptionDetails(ex, "respondToBrowser");
            }
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

    private void removeSocket(int token) {
        logger.log(Level.INFO, "Removing socket for token " + token);
        tokenSocketMap.remove(token);
    }

    private synchronized void updateSocketMap(SocketChannel browser, int token) {
        tokenSocketMap.put(token, browser);
        List<Integer> tokens = new ArrayList<Integer>();
        tokenSocketMap.forEach((tok, socket) -> {
            if (socket != null && !(socket.isOpen() || socket.isConnected()))
                tokens.add(tok);
        });
        tokens.forEach(tokenSocketMap::remove);
    }

    private synchronized void clearSocketMap() {
        Set<Integer> tokens = new HashSet<>(tokenSocketMap.keySet());
        tokens.forEach((tok) -> {
            try {
                tokenSocketMap.get(tok).close();
                tokenSocketMap.remove(tok);
            } catch (Exception ignored) {
            }
        });
    }

    void showExceptionDetails(Throwable t, String functionName) {
        logger.log(Level.SEVERE, t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage() + "\n" + t.fillInStackTrace());
        for (StackTraceElement stackTraceElement : t.getStackTrace()) {
            System.err.println(stackTraceElement.toString());
        }
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

    /**
     * getDataLength: Get the length of the data from the buffer. The actual data follows the token and length bytes.
     *
     * @param buf: The buffer
     * @return: The length of the data in the buffer
     */
    private int getDataLength(ByteBuffer buf) {
        final int position = buf.position();
        buf.position(tokenLength);
        int length = buf.getInt();
        buf.position(position); // Leave the position where the data starts.
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

    private void setConnectionClosedFlag(ByteBuffer buf) {
        buf.position(tokenLength + lengthLength);
        buf.put((byte) 1);
        setDataLength(buf, 0);
        buf.limit(headerLength);
        buf.position(0);
    }

    private byte getConnectionClosedFlag(ByteBuffer buf) {
        int position = buf.position();
        buf.position(tokenLength + lengthLength);
        byte flag = buf.get();
        buf.position(position);
        return flag;
    }

    /**
     * getToken: Get the token in the ByteBuffer
     *
     * @param buf: The buffer containing the token.
     * @return: The token
     */
    private int getToken(ByteBuffer buf) {
        return getToken(buf, 0);
    }

    /**
     * getToken: Get the token at position
     *
     * @param buf:      Buffer to get the token from
     * @param position: Position at which to get the token
     * @return The token at position
     */
    private int getToken(ByteBuffer buf, int position) {
        int originalPosition = buf.position();
        buf.position(position);
        int token = buf.getInt();
        buf.position(originalPosition);
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

    private void write(OutputStream os, ByteBuffer buf) throws IOException {
        os.write(buf.array(), buf.position(), buf.limit() - buf.position());
        os.flush();
        buf.position(buf.limit());
    }

    private int read(InputStream is, ByteBuffer buf) throws IOException {
        final int retVal = is.read(buf.array(), buf.position(), buf.capacity() - buf.position());
        if (retVal != -1) {
            buf.limit(buf.position() + retVal);
            buf.position(buf.limit());
        }
        return retVal;
    }

    ByteBuffer remainsOfPreviousBuffer = null;
    void splitMessages(ByteBuffer buf) {
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
                        try {
                            ByteBuffer newBuf = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.position() + lengthThisMessage));
                            newBuf.rewind();
                            //     logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                            combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                            respondToBrowser(newBuf);
                        } catch (Exception ex) {
                            showExceptionDetails(ex, "splitMessages");
                        }
                    }
                }
            }
            recycle(buf);
        } catch (Exception ex) {
            showExceptionDetails(ex, "splitMessages");
            reset();
        }
    }

    void splitHttpMessages(final byte[] buf, final int bytesRead, final OutputStream os, int token, final AtomicReference<byte[]> remainsOfPreviousMessage) {
        byte[] workBuf = remainsOfPreviousMessage.get() == null ? Arrays.copyOfRange(buf, 0, bytesRead) :
                ArrayUtils.addAll(remainsOfPreviousMessage.get(), Arrays.copyOfRange(buf, 0, bytesRead));
        int startIndex = 0;

        while (startIndex < workBuf.length) {
            final HttpMessage msg = new HttpMessage(workBuf, workBuf.length);
            if (!msg.headersBuilt) {
                // Don't know what this message is, just send it
                remainsOfPreviousMessage.set(null);
                try {
                    ByteBuffer nonHttp = getBuffer(token);
                    nonHttp.put(workBuf, 0, workBuf.length);
                    setDataLength(nonHttp, workBuf.length);
                    os.write(nonHttp.array(), 0, workBuf.length + headerLength);
                    os.flush();
                    recycle(nonHttp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            String hdrs = msg.getHeaders();
            int headersLength = hdrs.length();
            int messageLength = headersLength + msg.getContentLength();

            if (startIndex + messageLength <= workBuf.length) {
                List<String> js = new ArrayList<String>();
                js.add("JSESSIONID=" + JSESSIONID);
                msg.put("Cookie", js);

                try {
                    ByteBuffer output = getBuffer(token);
                    String headers = msg.getHeaders();
                    output.put(headers.getBytes(StandardCharsets.UTF_8));
                    if (msg.getContentLength() > 0)
                        output.put(msg.getMessageBody());
                    int dataLength = output.position() - headerLength;
                    setDataLength(output, dataLength);
                    setBufferForSend(output);
                    sendResponseToCloudProxy(output);
                } catch (Exception ex) {
                    System.out.println("ERROR: Exception in splitMessage when writing to stream: " + ex.getMessage());
                }
                startIndex += messageLength;
                remainsOfPreviousMessage.set(null);
            } else {
                remainsOfPreviousMessage.set(Arrays.copyOfRange(workBuf, startIndex, workBuf.length));
                break;
            }
        }
    }

    void reset() {
        if (cloudProxy != null && cloudProxy.isBound()) {
            try {
                cloudProxy.close();
            } catch (IOException ignored) {
            }
        }
        remainsOfPreviousBuffer = null;
        clearSocketMap();
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
    }

    private SSLSocket createSSLSocket(String host, int port) throws Exception {
        return SslUtil.createSSLSocket(host, port, this);
    }

    @Override
    public KeyManager[] getKeyManagers() throws GeneralSecurityException, IOException {
        return createKeyManagers(RestfulProperties.CLOUD_KEYSTORE_PATH, RestfulProperties.CLOUD_KEYSTORE_PASSWORD.toCharArray());
    }

    @Override
    public String getProtocol() {
        return "TLSv1.2";
    }

    @Override
    public TrustManager[] getTrustManagers() throws GeneralSecurityException, IOException {
        return createTrustManagers(RestfulProperties.TRUSTSTORE_PATH, RestfulProperties.TRUSTSTORE_PASSWORD.toCharArray());
    }

    int c = 0;

    void throwEx() throws Exception {
        if (c++ >= 20) {
            c = 0;
            throw new Exception("Contrived exception");
        }
    }
}

