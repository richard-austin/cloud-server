package com.proxy;

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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

import static com.proxy.SslUtil.*;

public class Cloud implements SslContextProvider {
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    private boolean running = false;
    private final ExecutorService browserWriteExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService browserReadExecutor = Executors.newCachedThreadPool();
    private final ExecutorService sendToCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService startCloudProxyInputProcessExecutor;
    private final ExecutorService acceptConnectionsFromCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService acceptConnectionsFromBrowserExecutor = Executors.newSingleThreadExecutor();

    private String NVRSESSIONID = "";

    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();

    private static final Logger logger = Logger.getLogger("CLOUD");
    public static final int BUFFER_SIZE = 1024;
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    private SSLSocket cloudProxy;
    private CloudProperties cloudProperties = CloudProperties.getInstance();
    private final int browserFacingPort = 8083, cloudProxyFacingPort = 8081;
    private final boolean protocolAgnostic = true;

    public void start() {
        if (!running) {
            running = true;
            acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
            acceptConnectionsFromBrowserExecutor.execute(() -> acceptConnectionsFromBrowser(browserFacingPort));
        }
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
                            reset();
                            cloudProxy.setSoTimeout(0);
                            cloudProxy.setSoLinger(true, 5);

                            this.cloudProxy = cloudProxy;
                            startCloudProxyInputProcess();
                            if (!protocolAgnostic)
                                authenticate();
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

    private void sendRequestToCloudProxy(ByteBuffer buf) {
        sendToCloudProxyExecutor.submit(() -> {
            try {
                OutputStream os = cloudProxy.getOutputStream();
                do {
                    write(os, buf);
                    os.flush();
                }
                while (buf.position() < buf.limit());
            } catch (Exception ex) {
                showExceptionDetails(ex, "startCloudProxyOutputProcess");
                reset();
            }
        });
    }

    public String authenticate() {
        boolean authOK = true;
        SSLSocket socket = this.cloudProxy;

        if (socket != null && !socket.isClosed()) {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();
                ByteBuffer buf = getBuffer(getToken());

                String payload = "username=" + cloudProperties.getUSERNAME() + "&password=" + cloudProperties.getPASSWORD();

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
                sendRequestToCloudProxy(buf);
                System.out.print(output);
                buf.clear();

                buf = stealMessage();
                HttpMessage hdrs = new HttpMessage(buf);
                var l = hdrs.getHeader("location");
                String location = l.size() == 1 ? l.get(0) : null;
                if (Objects.equals(location, "/")) {
                    List<String> setCookie = hdrs.getHeader("set-cookie");
                    for (String cookie : setCookie) {
                        if (cookie.startsWith("NVRSESSIONID")) {
                            final int startIdx = "NVRSESSIONID=".length();
                            final int sessionIdLen = 32;
                            NVRSESSIONID = cookie.substring(startIdx, startIdx + sessionIdLen);
                            break;
                        } else
                            NVRSESSIONID = "";
                    }
                } else {
                    authOK = false;
                    logger.warning("Authentication on NVR has failed");
                }
                String response = new String(buf.array(), headerLength, buf.limit() - headerLength);
                System.out.print(response);

            } catch (Exception ex) {
                authOK = false;
                System.out.println("Exception in authenticate: " + ex.getClass().getName() + ": " + ex.getMessage() + "\n\n");
                ex.printStackTrace();
            }
        }
        if (!authOK)
            NVRSESSIONID = "";
        return NVRSESSIONID;
    }

    public boolean logoff() {
        boolean retVal = true;
        if (cloudProxy != null && !cloudProxy.isClosed()) {
            try {
                OutputStream os = cloudProxy.getOutputStream();
                InputStream is = cloudProxy.getInputStream();
                ByteBuffer buf = getBuffer(getToken());
                String logoff = "GET /logoff HTTP/1.1\r\n" +
                        "Host: host\r\n" +
                        "Connection: keep-alive\r\n" +
                        "sec-ch-ua: \" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"\r\n" +
                        "sec-ch-ua-mobile: ?0\r\n" +
                        "sec-ch-ua-platform: \"Linux\"\r\n" +
                        "Upgrade-Insecure-Requests: 1\r\n" +
                        "User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36\r\n" +
                        "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9\r\n" +
                        "Sec-Fetch-Site: same-origin\r\n" +
                        "Sec-Fetch-Mode: navigate\r\n" +
                        "Sec-Fetch-User: ?1\r\n" +
                        "Sec-Fetch-Dest: document\r\n" +
                        "Referer: http://localhost:8083/\r\n" +
                        "Accept-Encoding: gzip, deflate, br\r\n" +
                        "Accept-Language: en-GB,en-US;q=0.9,en;q=0.8\r\n" +
                        "Cookie: NVRSESSIONID=" + NVRSESSIONID + "\r\n\r\n";

                System.out.println(logoff);
                buf.put(logoff.getBytes(StandardCharsets.UTF_8));
                setDataLength(buf, logoff.length());
                setBufferForSend(buf);
                sendRequestToCloudProxy(buf);   // Send logoff message
                buf.clear();
                buf = stealMessage();
                System.out.println(new String(buf.array(), headerLength, buf.limit()-headerLength));

            } catch (IOException ioex) {
                retVal = false;
            }
        }
        //reset();
        return retVal;
    }

    final Map<Integer, ByteBuffer> lastBitOfPreviousHttpMessage = new ConcurrentHashMap<>();

    final void readFromBrowser(SocketChannel channel, final int token) {
        browserReadExecutor.submit(() -> {

            ByteBuffer buf = getBuffer();
            try {
                while (channel.read(buf) != -1) {
                    splitHttpMessages(buf, token, lastBitOfPreviousHttpMessage);
                    buf = getBuffer();
                }
                setToken(buf, token);
                setConnectionClosedFlag(buf);
                sendRequestToCloudProxy(buf);
            } catch (IOException ignored) {
                removeSocket(token);
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                reset();
            }
        });
    }

    private boolean isHeartbeat(ByteBuffer buf) {
        // Dump the connection test heartbeats
        final String ignored = "Ignore";
        if (getToken(buf) == -1 && getDataLength(buf) == ignored.length()) {
            String strVal = new String(Arrays.copyOfRange(buf.array(), headerLength, buf.limit()), StandardCharsets.UTF_8);
            return ignored.equals(strVal);
        }
        return false;
    }

    private void respondToBrowser(ByteBuffer buf) {
        if (isHeartbeat(buf))
            return;

        browserWriteExecutor.submit(() -> {
            try {
//            logMessageMetadata(buf, "To browser");
                int token = getToken(buf);
                int length = getDataLength(buf);
                SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                if (getConnectionClosedFlag(buf) != 0) {
                    removeSocket(token);  // Usually already gone
                } else if (frontEndChannel != null && frontEndChannel.isOpen()) {
                    buf.position(headerLength);
                    buf.limit(headerLength + length);
                    int result;
                    try {
                        do {
                            result = frontEndChannel.write(buf);
                        }
                        while (result != -1 && buf.position() < buf.limit());
                    } catch (IOException ioex) {
                        logger.warning("IOException in respondToBrowser: " + ioex.getMessage());
                        setConnectionClosedFlag(buf);
                        sendRequestToCloudProxy(buf);
                        frontEndChannel.shutdownOutput().shutdownOutput().close();
                    }
                }
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
        setToken(buf, token);
        return buf;
    }

    private void setToken(ByteBuffer buf, int token) {
        buf.position(0);
        buf.putInt(token);
        buf.putInt(0);  // Reserve space for the data length
        buf.put((byte) 0); // Reserve space for the closed connection flag
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

    int nextToken = 0;

    /**
     * getToken: Get a sequential integer as a token
     *
     * @return: The token as an integer
     */
    private synchronized int getToken() {
        return ++nextToken;
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

    ByteBuffer lastBitOfPreviousMessage = null;

    void splitMessages(ByteBuffer buf) {
        try {
            buf.flip();
            ByteBuffer combinedBuf;

            if (lastBitOfPreviousMessage != null) {
                // Append the new buffer onto the previous ones remaining content
                combinedBuf = ByteBuffer.allocate(buf.limit() + lastBitOfPreviousMessage.limit() - lastBitOfPreviousMessage.position());
                combinedBuf.put(lastBitOfPreviousMessage);
                combinedBuf.put(buf);
                recycle(buf);
                lastBitOfPreviousMessage = null;
            } else
                combinedBuf = buf;
            combinedBuf.rewind();

            while (combinedBuf.position() < combinedBuf.limit()) {
                if (combinedBuf.limit() - combinedBuf.position() < headerLength) {
                    lastBitOfPreviousMessage = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
                    combinedBuf.position(combinedBuf.limit());
                } else {
                    int lengthThisMessage = getMessageLengthFromPosition(combinedBuf);
                    if (lengthThisMessage > combinedBuf.limit() - combinedBuf.position()) {
                        lastBitOfPreviousMessage = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.limit()));
                        combinedBuf.position(combinedBuf.limit());
                    } else {
                        try {
                            ByteBuffer newBuf = ByteBuffer.wrap(Arrays.copyOfRange(combinedBuf.array(), combinedBuf.position(), combinedBuf.position() + lengthThisMessage));
                            newBuf.rewind();
                            //     logger.log(Level.INFO, "Buffer size " + newBuf.limit() + " lengthThisMessage= " + lengthThisMessage);
                            combinedBuf.position(combinedBuf.position() + lengthThisMessage);
                            if (stealNextMessage)
                                synchronized (stealBufferWaitLock) {
                                    stolenBuffer = newBuf;
                                    stealBufferWaitLock.notify();
                                }
                            else {
                                respondToBrowser(newBuf);
                                logger.warning("Received "+newBuf.limit()+" bytes");
                            }
                        } catch (Exception ex) {
                            showExceptionDetails(ex, "splitMessages");
                        }
                    }
                }
            }
            recycle(combinedBuf);
        } catch (Exception ex) {
            showExceptionDetails(ex, "splitMessages");
            reset();
        }
    }

    private boolean stealNextMessage = false;
    private ByteBuffer stolenBuffer;
    private final Object stealBufferWaitLock = new Object();

    /**
     * stealMessage: Returns the buffer of the next message in splitMessages and prevents it from being sent to the connected browser.
     * This only applies to a single message, and the routing continues as normal thereafter.
     *
     * @return
     */
    private ByteBuffer stealMessage() {
        synchronized (stealBufferWaitLock) {
            try {
                stealNextMessage = true;
                logger.warning("Waiting...");
                stealBufferWaitLock.wait();
                logger.warning("Waiting done");
            } catch (InterruptedException ex) {
                logger.warning("Interrupted wait in stealMessage: " + ex.getMessage());
            }
            if (isHeartbeat(stolenBuffer))
                stealMessage();
            stealNextMessage = false;
        }
        return stolenBuffer;
    }

    void splitHttpMessages(final ByteBuffer buf, int token, final Map<Integer, ByteBuffer> lastBitOfPreviousBuffer) {
        buf.flip();
        ByteBuffer combinedBuf;

        if (lastBitOfPreviousBuffer.get(token) != null) {
            // Append the new buffer onto the previous ones remaining content
            combinedBuf = ByteBuffer.allocate(buf.limit() + lastBitOfPreviousBuffer.get(token).limit() - lastBitOfPreviousBuffer.get(token).position());
            combinedBuf.put(lastBitOfPreviousBuffer.get(token));
            combinedBuf.put(buf);

            combinedBuf.flip();
            lastBitOfPreviousBuffer.remove(token);
            recycle(buf);
        } else
            combinedBuf = buf;

        while (combinedBuf.position() < combinedBuf.limit()) {
            final HttpMessage msg = new HttpMessage(combinedBuf);
            if (protocolAgnostic || !msg.headersBuilt) {
                // Don't know what this message is, just send it
                try {
                    ByteBuffer nonHttp = ByteBuffer.allocate(combinedBuf.limit() + headerLength);
                    setToken(nonHttp, token);
                    nonHttp.put(combinedBuf);
                    setDataLength(nonHttp, combinedBuf.limit());
                    nonHttp.flip();
                    sendRequestToCloudProxy(nonHttp);
                    //recycle(nonHttp);
                    //  recycle(combinedBuf);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
            String hdrs = msg.getHeaders();
            int headersLength = hdrs.length();
            int messageLength = headersLength + msg.getContentLength();
            if (combinedBuf.position() + messageLength <= combinedBuf.limit()) {
                combinedBuf.position(combinedBuf.position() + messageLength);
                msg.remove("cookie");
                List<String> js = new ArrayList<>();
                js.add("NVRSESSIONID=" + NVRSESSIONID);
                msg.put("Cookie", js);
                try {
                    String headers = msg.getHeaders();
                    ByteBuffer output = ByteBuffer.allocate(headers.length() + msg.getContentLength() + headerLength);
                    setToken(output, token);
                    output.put(headers.getBytes(StandardCharsets.UTF_8));
                    if (msg.getContentLength() > 0)
                        output.put(msg.getMessageBody());
                    int dataLength = output.position() - headerLength;

                    setDataLength(output, dataLength);
                    setBufferForSend(output);
                    sendRequestToCloudProxy(output);
                } catch (Exception ex) {
                    System.out.println("ERROR: Exception in splitHttpMessages when writing to stream: " + ex.getClass().getName() + ex.getMessage());
                }
            } else {
                ByteBuffer remainingData = ByteBuffer.allocate(combinedBuf.limit() - combinedBuf.position());
                remainingData.put(combinedBuf);
                remainingData.flip();
                lastBitOfPreviousBuffer.put(token, remainingData);
                break;
            }
        }
    }

    public void reset() {
        logger.info("Reset called");
        if (startCloudProxyInputProcessExecutor != null)
            startCloudProxyInputProcessExecutor.shutdown();

        startCloudProxyInputProcessExecutor = Executors.newSingleThreadScheduledExecutor();
        if (cloudProxy != null && cloudProxy.isBound()) {
            try {
                cloudProxy.close();
            } catch (IOException ignored) {
            }
        }
        lastBitOfPreviousMessage = null;
        lastBitOfPreviousHttpMessage.clear();
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
        return createKeyManagers(cloudProperties.getCLOUD_KEYSTORE_PATH(), cloudProperties.getCLOUD_KEYSTORE_PASSWORD().toCharArray());
    }

    @Override
    public String getProtocol() {
        return "TLSv1.2";
    }

    @Override
    public TrustManager[] getTrustManagers() throws GeneralSecurityException, IOException {
        return createTrustManagers(cloudProperties.getTRUSTSTORE_PATH(), cloudProperties.getTRUSTSTORE_PASSWORD().toCharArray());
    }

    int c = 0;

    void throwEx() throws Exception {
        if (c++ >= 20) {
            c = 0;
            throw new Exception("Contrived exception");
        }
    }
}

