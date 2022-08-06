package com.proxy;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.proxy.cloudListener.CloudInstanceMap;
import com.proxy.cloudListener.CloudListener;
import grails.util.Holders;
import org.grails.web.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class Cloud {
    final Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    private boolean running = false;
    private ExecutorService browserWriteExecutor = null;
    private ExecutorService browserReadExecutor = null;
    private ExecutorService sendToCloudProxyExecutor = null;
    private ExecutorService cloudProxyInputProcessExecutor = null;

    private String NVRSESSIONID = "";

    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();

    private static final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private final int tokenLength = Integer.BYTES;
    private final int lengthLength = Integer.BYTES;
    private final int closedFlagLength = Byte.BYTES;
    private final int headerLength = tokenLength + lengthLength + closedFlagLength;
    private SSLSocket cloudProxy;
    final private String productId;
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private final CloudInstanceMap instances;
    // Remove browser (nvrSessionId) references after 120 seconds with no call coming in.
    //  This is twice the time between getTemperature calls which will refresh the timer as they come in, as
    //  will any other new request from the browser,
    private final long browserSessionTimeout = 120 * 1000;

    final private Map<String, Timer> sessionCountTimers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update = new JSONObject()
            .put("message", "update")
            .toString();

    public Cloud(SSLSocket cloudProxy, String productId, CloudInstanceMap instances) {
        this.cloudProxy = cloudProxy;
        this.productId = productId;
        this.instances = instances;
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();
        brokerMessagingTemplate = (SimpMessagingTemplate) ctx.getBean("brokerMessagingTemplate");
        sessionCountTimers = new ConcurrentHashMap<>();
    }

    /**
     * start: Start the threads in the Cloud instance
     */
    public void start() {
        if (!running) {
            setLogLevel(cloudProperties.getLOG_LEVEL());
            logger.info("Starting Cloud instance for " + productId);
            running = true;
            browserWriteExecutor = Executors.newSingleThreadExecutor();
            browserReadExecutor = Executors.newCachedThreadPool();
            sendToCloudProxyExecutor = Executors.newSingleThreadExecutor();
            cloudProxyInputProcessExecutor = Executors.newSingleThreadExecutor();
            startCloudProxyInputProcess();
        }
    }

    public void stop() {
        try (SSLSocket ignored = cloudProxy) {
            logger.info("Stopping Cloud instance for " + productId);
            browserWriteExecutor.shutdownNow();
            browserReadExecutor.shutdownNow();
            sendToCloudProxyExecutor.shutdownNow();

            if (cloudProxyInputProcessExecutor != null)
                cloudProxyInputProcessExecutor.shutdownNow();

            lastBitOfPreviousMessage = null;
            clearTokenSocketMap();
            running = false;
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in stop: " + ex.getMessage());
        }
    }

    private void startCloudProxyInputProcess() {
        cloudProxyInputProcessExecutor.submit(() -> {
            try {
                if (cloudProxy != null && !cloudProxy.isClosed()) {
                    ByteBuffer buf = getBuffer();
                    buf.flip();
                    InputStream is = cloudProxy.getInputStream();
                    while (read(is, buf) != -1) {
                        separateMessages(buf);
                        buf = getBuffer();
                    }
                    recycle(buf);
                    logger.error("CloudProxy reader socket has closed in startCloudProxyInputProcess");
                }
            } catch (Exception exx) {
                showExceptionDetails(exx, "startCloudProxyInputProcess");
            }
        });
    }

    private void sendRequestToCloudProxy(ByteBuffer buf) {
        sendToCloudProxyExecutor.submit(() -> {
            try {
                OutputStream os = cloudProxy.getOutputStream();
                do {
                    //   System.out.println(new String(buf.array(), headerLength, buf.limit()));
                    write(os, buf);
                    os.flush();
                }
                while (buf.position() < buf.limit());
                if (getDataLengthFullRestore(buf) != 6)
                    logger.debug("sendRequestToCloudProxy length: " + getDataLengthFullRestore(buf));

            } catch (Exception ex) {
                showExceptionDetails(ex, "sendRequestToCloudProxy");
            }
        });
    }

    public String authenticate() {
        SSLSocket socket = this.cloudProxy;
        NVRSESSIONID = "";
        if (socket != null && !socket.isClosed()) {
            try {
                String payload = "username=" + cloudProperties.getUSERNAME() + "&password=" + cloudProperties.getPASSWORD();

                String output = "POST /login/authenticate HTTP/1.1\r\n" +
                        "Host: " + socket.getInetAddress().getHostAddress() + "\r\n" +
                        "DNT: 1\r\n" +
                        "Upgrade-Insecure-Requests: 1\r\n" +
                        "X-Auth-Token: 7yk=zJu+@77x@MTJG2HD*YLJgvBthkW!\r\n" +
                        "Content-type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: " + payload.length() + "\r\n\r\n" +
                        payload + "\r\n";

                System.out.print(output);
                ByteBuffer buf = doRequestResponse(output);

                if (buf != null) {
                    HttpMessage hdrs = new HttpMessage(buf);
                    var l = hdrs.getHeader("location");
                    String location = (l != null && l.size() == 1) ? l.get(0) : null;
                    if (location != null && !location.contains("authfail")) {
                        List<String> setCookie = hdrs.getHeader("set-cookie");
                        for (String cookie : setCookie) {
                            if (cookie.startsWith("NVRSESSIONID")) {
                                final int startIdx = "NVRSESSIONID=".length();
                                final int sessionIdLen = 32;
                                NVRSESSIONID = cookie.substring(startIdx, startIdx + sessionIdLen);
                                break;
                            }
                        }
                    } else {
                        logger.warn("Authentication on NVR has failed");
                    }
                    String response = new String(buf.array(), headerLength, buf.limit() - headerLength);
                    System.out.print(response);
                } else
                    logger.error("Couldn't log onto NVR, no message returned for logon request");

            } catch (Exception ex) {

                System.out.println(ex.getClass().getName() + " in authenticate: " + ex.getClass().getName() + ": " + ex.getMessage() + "\n\n");
                ex.printStackTrace();
            }
        } else
            NVRSESSIONID = "NO_CONN";
        return NVRSESSIONID;
    }

    /**
     * logoff: Finish the session on the NVR
     *
     * @param cookie: The NVR session cookie
     * @return: true on success
     */
    public boolean logoff(String cookie) {
        boolean retVal = true;
        if (cloudProxy != null && !cloudProxy.isClosed()) {
            try {
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
                        "Cookie: NVRSESSIONID=" + cookie + "\r\n\r\n";

                System.out.println(logoff);
                ByteBuffer buf = doRequestResponse(logoff);
                if (buf != null)
                    logger.info(new String(buf.array(), headerLength, buf.limit() - headerLength));

            } catch (Exception ioex) {
                logger.error(ioex.getClass().getName() + " in logoff: " + ioex.getMessage());
                retVal = false;
            }
        }
        return retVal;
    }

    public final void readFromBrowser(final SocketChannel channel, final ByteBuffer initialBuf, final int token, final String nvrSessionId) {
        browserReadExecutor.submit(() -> {
            try {
                if (!nvrSessionId.equals(""))
                    createSessionTimeout(nvrSessionId);  // Reset the instance count timeout for this session id

                updateSocketMap(channel, token);
                ByteBuffer buf = initialBuf;
                // Send the initial message delivered from the CloudListener
                packageAndSendToCloudProxy(buf, token);
                //splitHttpMessages(buf, token, lastBitOfPreviousHttpMessage);

                // Now read any more that may still come through
                buf = getBuffer();
                logger.debug("Into read with token " + token);
                while (channel.read(buf) != -1) {
                    packageAndSendToCloudProxy(buf, token);
                    //splitHttpMessages(buf, token, lastBitOfPreviousHttpMessage);
                    buf = getBuffer();
                }
                logger.debug("Out of read with token " + token);

                setToken(buf, token);
                setConnectionClosedFlag(buf);
                sendRequestToCloudProxy(buf);
                removeSocket(token);
            } catch (IOException ignored) {
                removeSocket(token);
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                // reset();
            }
        });
    }

    private boolean isHeartbeat(ByteBuffer buf) {
        final int position = buf.position();
        // Dump the connection test heartbeats
        final String ignored = "Ignore";
        if (getToken(buf) == -1 && getDataLength(buf) == ignored.length()) {
            instances.resetNVRTimeout(getProductId());  // Reset the timeout which would remove this Cloud instance from the map
            String strVal = new String(Arrays.copyOfRange(buf.array(), headerLength, buf.limit()), StandardCharsets.UTF_8);
            return ignored.equals(strVal);
        }
        buf.position(position);
        return false;
    }

    private void respondToBrowser(ByteBuffer buf) {
        if (isHeartbeat(buf)) {
            sendRequestToCloudProxy(buf);   // Bounce the heartbeat back to the CloudProxy to show we are still connected
            return;
        }

        browserWriteExecutor.submit(() -> {
            try {
//            logMessageMetadata(buf, "To browser");
                int token = getToken(buf);
                int length = getDataLength(buf);
                SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                if (getConnectionClosedFlag(buf) != 0) {
                    removeSocket(token);  // Usually already gone
                    // frontEndChannel.close();
                } else if (frontEndChannel != null && frontEndChannel.isOpen()) {
                    buf.position(headerLength);
                    buf.limit(headerLength + length);
                    int result;
                    logger.debug("respondToBrowser length: " + getDataLengthFullRestore(buf));

                    try {
                        do {
                            result = frontEndChannel.write(buf);
                        }
                        while (result != -1 && buf.position() < buf.limit());
                    } catch (IOException ioex) {
                        logger.warn("IOException in respondToBrowser: " + ioex.getMessage());
                        setConnectionClosedFlag(buf);
                        sendRequestToCloudProxy(buf);
                        frontEndChannel.shutdownOutput().close();
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
        try (var skt = tokenSocketMap.remove(token)) {
            logger.debug("Removing socket for token " + token);
        } catch (IOException ex) {
            showExceptionDetails(ex, "removeSocket");
        }
    }

    private synchronized void updateSocketMap(SocketChannel browser, int token) {
        tokenSocketMap.put(token, browser);
        List<Integer> tokens = new ArrayList<>();
        tokenSocketMap.forEach((tok, socket) -> {
            if (socket != null && !(socket.isOpen() || socket.isConnected()))
                tokens.add(tok);
        });
        tokens.forEach(tokenSocketMap::remove);
    }

    private synchronized void clearTokenSocketMap() {
        Set<Integer> tokens = new HashSet<>(tokenSocketMap.keySet());
        tokens.forEach((tok) -> {
            try (SocketChannel sock = tokenSocketMap.remove(tok)) {
                logger.debug("Removing socket for token " + tok.toString());
            } catch (Exception ignored) {
            }
        });
    }

    void showExceptionDetails(Throwable t, String functionName) {
        logger.error(t.getClass().getName() + " exception in " + functionName + ": " + t.getMessage() + "\n" + t.fillInStackTrace());
//        for (StackTraceElement stackTraceElement : t.getStackTrace()) {
//            System.err.println(stackTraceElement.toString());
//        }
    }

    public String getProductId() {
        return productId;
    }

    /**
     * getBuffer: Get a new ByteBuffer of BUFFER_SIZE bytes length.
     *
     * @return: The buffer
     */
    private ByteBuffer getBuffer() {
        return CloudListener.getBuffer();
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
        CloudListener.setToken(buf, token);
    }

    private void recycle(ByteBuffer buf) {
        CloudListener.recycle(buf);
    }

    /**
     * setDataLength: Set the lengthLength bytes following the token to the length of the data in the buffer
     * (minus token and length bytes).
     *
     * @param buf:    The buffer to set the length in.
     * @param length: The length to set.
     */
    private void setDataLength(ByteBuffer buf, int length) {
        CloudListener.setDataLength(buf, length);
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

    private int getDataLengthFullRestore(ByteBuffer buf) {
        int limit = buf.limit();
        int position = buf.position();
        int length = getDataLength(buf);
        buf.limit(limit);
        buf.position(position);

        return length;
    }

    /**
     * getToken: Get a sequential integer as a token
     *
     * @return: The token as an integer
     */
    private synchronized int getToken() {
        return CloudListener.get_nextToken();
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

    /**
     * separateMessages: Ensures the messages read from the CloudProxy through the single channel link are not
     * appended to each other and that if a message has been truncated, it will append the remain part
     * to it.
     *
     * @param buf The buffer containing bytes read from the CloudProxy on the NVR
     */
    void separateMessages(ByteBuffer buf) {
        try {
            buf.flip();
            ByteBuffer combinedBuf;

            if (lastBitOfPreviousMessage != null) {
                // Append the new buffer onto the previous ones remaining content
                combinedBuf = ByteBuffer.allocate(buf.limit() + lastBitOfPreviousMessage.limit() - lastBitOfPreviousMessage.position());
                combinedBuf.put(lastBitOfPreviousMessage);
                combinedBuf.put(buf);
                lastBitOfPreviousMessage = null;
            } else {
                combinedBuf = getBuffer();
                combinedBuf.put(buf);
            }
            combinedBuf.flip();
            recycle(buf);

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
                            if (!stealMessage(newBuf)) {
                                respondToBrowser(newBuf);
                                logger.trace("Received " + newBuf.limit() + " bytes");
                            }
                        } catch (Exception ex) {
                            showExceptionDetails(ex, "separateMessages");
                            lastBitOfPreviousMessage = null;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            showExceptionDetails(ex, "separateMessages");
            lastBitOfPreviousMessage = null;
        }
    }

    /**
     * doRequestResponse: Send a request to the webserver, and grab the response before it's sent to the web browser.
     * Return the response in its ByteBuffer.
     *
     * @param request: The request as a string
     * @return: The response in a ByteBuffer
     */
    private ByteBuffer doRequestResponse(String request) {
        final int token = getToken();
        ByteBuffer buf = getBuffer(token);
        setDataLength(buf, request.length());
        buf.put(request.getBytes());
        setBufferForSend(buf);
        sendRequestToCloudProxy(buf);
        ByteBuffer retVal = stealMessage(token);
        recycle(buf);
        return retVal;
    }

    /**
     * stealMessage: Check if the token of the message is in the stolenBuffers map. If so put the buffer in the map
     * against its token and return true, else return false.
     *
     * @param buf: The buffer with the message and token.
     * @return: true if message is redirected else false.
     */
    private boolean stealMessage(ByteBuffer buf) {
        boolean retVal = false;
        Integer token = getToken(buf);
        if (stolenBuffers.containsKey(token)) {
            BufferLockobject blo = stolenBuffers.get(token);
            synchronized (blo.lockObject) {
                blo.setBuffer(buf);
                blo.lockObject.notify();
                retVal = true;
            }
        }
        return retVal;
    }

    final private Map<Integer, BufferLockobject> stolenBuffers = new ConcurrentHashMap<>();

    /**
     * stealMessage: returns the message whose token matches the given token.
     *
     * @param token: The token of the message to be "stolen"
     * @return, Message with matching token
     */
    private ByteBuffer stealMessage(int token) {
        BufferLockobject blo = new BufferLockobject();
        stolenBuffers.put(token, blo);
        ByteBuffer retVal = null;
        synchronized (blo.lockObject) {
            try {
                logger.debug("Waiting...");
                blo.lockObject.wait(5000);
                logger.debug("Waiting done");
                retVal = blo.getBuffer();
                if (retVal == null)
                    throw new Exception("Wait for response timed out, no message returned.");
            } catch (InterruptedException ex) {
                logger.warn("Interrupted wait in stealMessage: " + ex.getMessage());
            } catch (Exception ex) {
                logger.trace(ex.getClass().getName() + " in stealMessage: " + ex.getMessage());
            }
        }
        stolenBuffers.remove(token);
        return retVal;
    }

    /**
     * packageAndSendToCloudProxy: Set up the message in a buffer wih token and message length to send to the CloudProxy
     *
     * @param buf    The raw buffer (no token or other CloudProxy protocol related information
     * @param token: The token (represents the socket to use)
     */
    void packageAndSendToCloudProxy(ByteBuffer buf, final int token) {
        try {
            buf.flip();
            ByteBuffer bufToSend = ByteBuffer.allocate(buf.limit() + headerLength);
            setToken(bufToSend, token);
            bufToSend.put(buf);
            setDataLength(bufToSend, buf.limit());
            logger.debug("readFromBrowser length: " + getDataLengthFullRestore(bufToSend));
            bufToSend.flip();
            sendRequestToCloudProxy(bufToSend);
        } catch (Exception ex) {
            logger.trace(ex.getClass().getName() + " in sendToCloudProxy: " + ex.getMessage());
        }
    }

    public void setCloudProxyConnection(SSLSocket cloudProxy) {
        stop();
        this.cloudProxy = cloudProxy;
        start();
    }

    void setLogLevel(String level) {
        logger.setLevel(Objects.equals(level, "INFO") ? Level.INFO :
                Objects.equals(level, "DEBUG") ? Level.DEBUG :
                        Objects.equals(level, "TRACE") ? Level.TRACE :
                                Objects.equals(level, "WARN") ? Level.WARN :
                                        Objects.equals(level, "ERROR") ? Level.ERROR :
                                                Objects.equals(level, "OFF") ? Level.OFF :
                                                        Objects.equals(level, "ALL") ? Level.ALL : Level.OFF);
    }

    private int getMessageLengthFromPosition(ByteBuffer buf) {
        return buf.getInt(buf.position() + tokenLength) + headerLength;
    }

    int c = 0;

    void throwEx() throws Exception {
        if (c++ >= 20) {
            c = 0;
            throw new Exception("Contrived exception");
        }
    }

    public void incSessionCount(String nvrSessionId) {
        if (!sessionCountTimers.containsKey(nvrSessionId)) {
            createSessionTimeout(nvrSessionId);
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        }
    }

    public void decSessionCount(String nvrSessionId) {
        if (sessionCountTimers.containsKey(nvrSessionId)) {
            sessionCountTimers.get(nvrSessionId).cancel();
            sessionCountTimers.get(nvrSessionId).purge();
            sessionCountTimers.remove(nvrSessionId);
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update);
        }
    }

    void createSessionTimeout(String nvrSessionId) {
        TimerTask task = new SessionCountTimerTask(nvrSessionId, this);

        Timer timer = new Timer(nvrSessionId);
        timer.schedule(task, browserSessionTimeout);
        if (sessionCountTimers.containsKey(nvrSessionId))
            sessionCountTimers.get(nvrSessionId).cancel();

        sessionCountTimers.put(nvrSessionId, timer);
    }

    public int getSessionCount() {
        return sessionCountTimers.size();
    }
}

