package com.proxy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.cloudwebapp.beans.AppContextManager;
import com.cloudwebapp.messaging.UpdateMessage;
import com.proxy.cloudListener.*;
import com.proxy.cloudListener.CloudMQListener;
import org.slf4j.LoggerFactory;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import jakarta.jms.*;
import jakarta.jms.Session;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.proxy.CloudMQ.MessageMetadata.*;

public class CloudMQ {
    public enum MessageMetadata {
        HEARTBEAT("heartbeat"),
        TOKEN("token"),
        CONNECTION_CLOSED("connectionClosed"),
        CLOUD_PROXY_CORRELATION_ID("cloudProxy");
        final String value;

        MessageMetadata(String value) {
            this.value = value;
        }
    }

    private boolean running = false;
    final private static Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    public static final int BUFFER_SIZE = 16384;
    private ExecutorService browserWriteExecutor = null;
    private ExecutorService browserReadExecutor = null;
    private ExecutorService sendToCloudProxyExecutor = null;
    private ScheduledExecutorService cloudConnectionCheckExecutor;
    private final Set<String> loggingOff = new HashSet<>();

    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();

    private static final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private Session cloudProxySession;
    private CloudProxyReaderWriter readerWriter;
    final private String productId;
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private final CloudMQInstanceMap instances;

    final private Map<String, Timer> sessionCountTimers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update;

    public CloudMQ(Session session, String productId, CloudMQInstanceMap instances) {
        try {
            update  = new JSONObject()
                    .put("message", "update")
                    .toString();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        this.cloudProxySession = session;
        this.productId = productId;
        this.instances = instances;
        ApplicationContext ctx =  AppContextManager.getAppContext();// Holders.getGrailsApplication().getMainContext();
        brokerMessagingTemplate = (SimpMessagingTemplate) ctx.getBean("brokerMessagingTemplate");
        sessionCountTimers = new ConcurrentHashMap<>();
    }

    /**
     * start: Start the threads in the CloudMQ instance
     */
    public void start() {
        if (!running) {
            try {
                setLogLevel(cloudProperties.getLOG_LEVEL());
                logger.info("Starting CloudMQ instance for " + productId);
                running = true;
                browserWriteExecutor = Executors.newSingleThreadExecutor();
                browserReadExecutor = Executors.newCachedThreadPool();
                sendToCloudProxyExecutor = Executors.newSingleThreadExecutor();
                cloudConnectionCheckExecutor = Executors.newSingleThreadScheduledExecutor();
                readerWriter = new CloudProxyReaderWriter();
                startCloudProxyConnectionCheck();
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudMQ.start: " + ex.getMessage());
            }
        }
    }

    public void stop() {
        try {
            logger.info("Stopping CloudMQ instance for " + productId);
            running = false;
            browserWriteExecutor.shutdownNow();
            browserReadExecutor.shutdownNow();
            sendToCloudProxyExecutor.shutdownNow();
            cloudConnectionCheckExecutor.shutdownNow();
            readerWriter.stop();
            clearTokenSocketMap();

        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in stop: " + ex.getMessage());
        }
    }

    private class CloudProxyReaderWriter implements MessageListener {
        private Destination cloudProxyResponseTopic;
        private MessageConsumer cloudProxyConsumer = null;
        private MessageProducer cloudProxyProducer = null;

        CloudProxyReaderWriter() {
            try {
                Destination cloudProxyTopic = cloudProxySession.createTopic(productId);   // Create a queue with the NVR product id as the name
                cloudProxyResponseTopic = cloudProxySession.createTemporaryTopic();

                cloudProxyProducer = cloudProxySession.createProducer(cloudProxyTopic);
                //  cloudProxyProducer.setTimeToLive(1000);
                cloudProxyConsumer = cloudProxySession.createConsumer(cloudProxyResponseTopic);

                cloudProxyConsumer.setMessageListener(this);
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.constructor: " + ex.getMessage());
            }
        }

        void write(Message bm) {
            try {
                bm.setJMSReplyTo(cloudProxyResponseTopic);
                cloudProxyProducer.send(bm);
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.write: " + ex.getMessage());
            }
        }

        void stop() {
            try {
                // Close the consumer and producer in a thread as this may block
                Thread t = new Thread(() -> {
                    try {
                        cloudProxyConsumer.close();
                        cloudProxyProducer.close();
                    } catch (Exception exception) {
                        logger.error(exception.getClass().getName() + " in CloudProxyReaderWriter.stop: " + exception.getMessage());
                    }
                });
                t.start();
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.stop: " + ex.getMessage());
            }
        }

        @Override
        public void onMessage(Message message) {
            try {
                if (message.getBooleanProperty(HEARTBEAT.value)) {
                    logger.debug("Heartbeat received");
                    try {
                        instances.resetNVRTimeout(getProductId());  // Reset the timeout which would otherwise  remove this CloudMQ instance from the map
                    } catch (Exception ex) {
                        logger.error(ex.getClass().getName() + " in handleHeartbeat: " + ex.getMessage());
                    }
                } else if (message.getBooleanProperty(CONNECTION_CLOSED.value)) {
                    final int token = message.getIntProperty(TOKEN.value);
                    removeSocket(token);
                } else if (message instanceof BytesMessage bm) {
                    // If it's a request/response response, put in the responseMessage store and notify the doRequestResponse cal
                    if (responseLock.get() == message.getIntProperty(TOKEN.value)) {
                        synchronized (responseLock) {
                            responseMessage = bm;
                            responseLock.notify();
                        }
                    } else if (Objects.equals(bm.getJMSCorrelationID(), CLOUD_PROXY_CORRELATION_ID.value)) {
                        if(bm.getBodyLength() > 0)
                            respondToBrowser(bm);
                        logger.trace("Received message for token "+bm.getIntProperty(TOKEN.value));
                    } else
                        logger.warn("Received unexpected correlation ID " + bm.getJMSCorrelationID());
                } else {
                    logger.warn("Unhandled message type (" + message.getClass().getName() + " in CloudProxyReaderWriter.onMessage");
                }
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.onMessage: " + ex.getMessage());
            }
        }
    }

    private void sendRequestToCloudProxy(Message bm) {
        readerWriter.write(bm);
    }

    public String authenticate() {
        Session cloudProxySession = this.cloudProxySession;
        String NVRSESSIONID = "";
        if (cloudProxySession != null) {
            try {
                String localIP;
                try (final DatagramSocket socket = new DatagramSocket()) {
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                    localIP = socket.getLocalAddress().getHostAddress();
                }
                String payload = "username=" + cloudProperties.getUSERNAME() + "&password=" + cloudProperties.getPASSWORD();

                String output = "POST /login/authenticate HTTP/1.1\r\n" +
                        "Host: " + localIP + "\r\n" +
                        "DNT: 1\r\n" +
                        "Upgrade-Insecure-Requests: 1\r\n" +
                        "X-Auth-Token: 7yk=zJu+@77x@MTJG2HD*YLJgvBthkW!\r\n" +
                        "Content-type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: " + payload.length() + "\r\n\r\n" +
                        payload + "\r\n";

                BytesMessage response = doRequestResponse(output);

                if (response != null) {
                    HttpMessage hdrs = new HttpMessage(response);
                    var l = hdrs.getHeader("location");
                    String location = (l != null && l.size() == 1) ? l.getFirst() : null;
                    if (location != null && !location.contains("authfail")) {
                        List<String> setCookie = hdrs.getHeader("set-cookie");
                        for (String cookie : setCookie) {
                            if (cookie.startsWith("NVRSESSIONID")) {
                                final int startIdx = "NVRSESSIONID=".length();
                                final int sessionIdLen = 32;
                                NVRSESSIONID = cookie.substring(startIdx, startIdx + sessionIdLen);
                                incSessionCount(NVRSESSIONID);
                                break;
                            }
                        }
                    } else {
                        logger.warn("Authentication on NVR has failed");
                    }
                    System.out.print(response);
                } else
                    logger.error("Couldn't log onto NVR, no message returned for logon request");

            } catch (Exception ex) {

                System.out.println(ex.getClass().getName() + " in authenticate: " + ex.getClass().getName() + ": " + ex.getMessage() + "\n\n");
            }
        } else
            NVRSESSIONID = "NO_CONN";
        return NVRSESSIONID;
    }

    /**
     * logoff: Finish the session on the NVR
     *
     * @param nvrSessionId: The NVR session cookie
     * @return: true on success
     */
    public boolean logoff(String nvrSessionId) {
        boolean retVal = true;
        loggingOff.add(nvrSessionId);
        if (cloudProxySession != null) {
            try {
                String logoff = "GET /logout HTTP/1.1\r\n" +
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
                        "Cookie: NVRSESSIONID=" + nvrSessionId + "\r\n\r\n";

                System.out.println(logoff);
                TextMessage response = (TextMessage) doRequestResponse(logoff);
                if (response != null)
                    logger.info(response.getText());

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
                if (!nvrSessionId.isEmpty())
                    createSessionTimeout(nvrSessionId);  // Reset the instance count timeout for this session id

                updateSocketMap(channel, token);
                ByteBuffer buf = initialBuf;
                logger.trace("readFromBrowser length: " + buf.limit());
                // Send the initial message delivered from the CloudMQListener
                packageAndSendToCloudProxy(buf, token);
                // Now read any more that may still come through
                buf = getBuffer();
                while (channel.read(buf) != -1) {
                    logger.trace("readFromBrowser length: " + buf.limit());
                    packageAndSendToCloudProxy(buf, token);
                    recycle(buf);
                    buf = getBuffer();
                }
                recycle(buf);
                sendCloseConnection(token);
                removeSocket(token);
            } catch (IOException ignored) {
                if (tokenSocketMap.containsKey(token))
                    removeSocket(token);
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                // reset();
            }
        });
    }

    private void respondToBrowser(BytesMessage bm) {
        try {
            browserWriteExecutor.submit(() -> {
                try {
                    int token = bm.getIntProperty("token");
                    int length = (int) bm.getBodyLength();
                    SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                    if (frontEndChannel != null && frontEndChannel.isOpen()) {
                        ByteBuffer buf = length > BUFFER_SIZE ? ByteBuffer.allocate(length) : getBuffer();
                        bm.readBytes(buf.array());
                        buf.limit(length);
                        int result;
                        logger.trace("respondToBrowser length: " + length);

                        try {
                            do {
                                result = frontEndChannel.write(buf);
                            }
                            while (result != -1 && buf.position() < buf.limit());
                            if (length == BUFFER_SIZE)
                                recycle(buf);
                        } catch (IOException ioex) {
                            logger.warn("IOException in respondToBrowser: " + ioex.getMessage());
//                            bm.setBooleanProperty(CONNECTION_CLOSED.value, true);
//                            bm.setIntProperty(TOKEN.value, token);
//                            sendRequestToCloudProxy(bm);
                            frontEndChannel.shutdownOutput().close();
                            sendCloseConnection(token);
                        }
                    }
                    else
                        sendCloseConnection(token);
                } catch (Exception ex) {
                    showExceptionDetails(ex, "respondToBrowser");
                }
            });
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in respondToBrowser: " + ex.getMessage());
        }
    }

    void sendCloseConnection(int token) {
        try {
            Message bm = cloudProxySession.createMessage();
            bm.setIntProperty(TOKEN.value, token);
            bm.setBooleanProperty(CONNECTION_CLOSED.value, true);
            sendRequestToCloudProxy(bm);
        } catch (JMSException ex) {
            logger.error(ex.getClass().getName() + " in sendCloseConnection: " + ex.getMessage());
        }
    }

    private void removeSocket(int token) {
        try (var ignored = tokenSocketMap.remove(token)) {
            logger.debug("Removing and closing socket for token " + token);
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
            try (SocketChannel ignored1 = tokenSocketMap.remove(tok)) {
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
    public static ByteBuffer getBuffer() {
        ByteBuffer buf = Objects.requireNonNullElseGet(bufferQueue.poll(), () -> ByteBuffer.allocate(BUFFER_SIZE));
        buf.clear();
        return buf;
    }

    public static synchronized void recycle(ByteBuffer buf) {
        buf.clear();
        bufferQueue.add(buf);
    }

    /**
     * getToken: Get a sequential integer as a token
     *
     * @return: The token as an integer
     */
    private synchronized int getToken() {
        return CloudMQListener.getToken();
    }


    TemporaryQueue replyTo;

    private void startCloudProxyConnectionCheck() {
        try {
            final BytesMessage msg = cloudProxySession.createBytesMessage();
            msg.setBooleanProperty(HEARTBEAT.value, true);
            replyTo = cloudProxySession.createTemporaryQueue();
            msg.setJMSReplyTo(replyTo);
            cloudConnectionCheckExecutor.scheduleAtFixedRate(() -> {
                try {
                    if (running)
                        readerWriter.write(msg);
                } catch (Exception ex) {
                    logger.error(ex.getClass().getName() + " in cloudConnectionCheck: " + ex.getMessage());
                    //   restart();
                }
            }, 10, 10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            showExceptionDetails(ex, "startCloudConnectionCheck");
            //  restart();
        }
    }

    final AtomicInteger responseLock = new AtomicInteger();
    private BytesMessage responseMessage = null;

    /**
     * doRequestResponse: Send a request to the webserver, and grab the response before it's sent to the web browser.
     * Return the response in its ByteBuffer.
     *
     * @param request: The request as a string
     * @return: The response in a ByteBuffer
     */
    private BytesMessage doRequestResponse(String request) {
        try {
            synchronized (responseLock) {
                responseMessage = null;
                final int token = getToken();
                BytesMessage tm = cloudProxySession.createBytesMessage();
                tm.writeBytes(request.getBytes(), 0, request.length());
                tm.setIntProperty(TOKEN.value, token);
                responseLock.set(token);
                readerWriter.write(tm);
                responseLock.wait(1000);
                return responseMessage;
            }
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in doRequestResponse: " + ex.getMessage());
        }
        return null;
    }

    /**
     * packageAndSendToCloudProxy: Set up the message in a BytesMessage wih token and message length to send to the CloudProxy
     *
     * @param buf    The raw buffer (no token or other CloudProxy protocol related information
     * @param token: The token (represents the socket to use)
     */
    void packageAndSendToCloudProxy(ByteBuffer buf, final int token) {
        try {
            buf.flip();
            BytesMessage bm = cloudProxySession.createBytesMessage();
            bm.writeBytes(buf.array(), 0, buf.limit());
            bm.setIntProperty("token", token);
            sendRequestToCloudProxy(bm);
        } catch (Exception ex) {
            logger.trace(ex.getClass().getName() + " in sendToCloudProxy: " + ex.getMessage());
        }
    }

    public void setCloudProxyConnection(Session cloudProxySession) {
        stop();
        this.cloudProxySession = cloudProxySession;
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

    public void incSessionCount(String nvressionid) {
        if (!nvressionid.isEmpty() && !sessionCountTimers.containsKey(nvressionid)) {
            var timer = createTimer(nvressionid);
            sessionCountTimers.put(nvressionid, timer);
            var updateMessage = new UpdateMessage(productId, "usersConnected", sessionCountTimers.size());
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", updateMessage);
        }
    }

    public void decSessionCount(String nvrSessionId) {
        if (!nvrSessionId.isEmpty() && sessionCountTimers.containsKey(nvrSessionId)) {
            sessionCountTimers.get(nvrSessionId).cancel();
            sessionCountTimers.get(nvrSessionId).purge();
            sessionCountTimers.remove(nvrSessionId);
            var updateMessage = new UpdateMessage(productId, "usersConnected", sessionCountTimers.size());
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", updateMessage);
        }
    }

    void createSessionTimeout(String nvrSessionId) {
        if (!nvrSessionId.isEmpty()) {
            if (!loggingOff.contains(nvrSessionId)) {  // Prevent the session count timer being reset if we are logging off
                if (sessionCountTimers.containsKey(nvrSessionId)) {
                    Timer timer = createTimer(nvrSessionId);
                    sessionCountTimers.get(nvrSessionId).cancel();
                    sessionCountTimers.put(nvrSessionId, timer);
                }
            } else {
                // Delay the removal of the session id from the logging off set as other calls may occur with that ID as well as logoff
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        loggingOff.remove(nvrSessionId);
                    }
                }, 2000); // Delay for two seconds
            }
        }
    }

    Timer createTimer(String nvrSessionId) {
        TimerTask task = new SessionCountTimerTask(nvrSessionId, this);
        Timer timer = new Timer(nvrSessionId);
        // Remove browser (nvrSessionId) references after 120 seconds with no call coming in.
        //  This is twice the time between getTemperature calls which will refresh the timer as they come in, as
        //  will any other new request from the browser,
        long browserSessionTimeout = 120 * 1000;
        timer.schedule(task, browserSessionTimeout);
        return timer;
    }

    public int getSessionCount() {
        System.out.println("getSessionCount: " + sessionCountTimers.size());
        sessionCountTimers.forEach((uniqueId, sessionCountTimer) -> {
            System.out.println("       SessionID: " + uniqueId);
        });

        return sessionCountTimers.size();
    }
}

