package com.proxy;

import javax.jms.*;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.proxy.cloudListener.*;
import grails.util.Holders;
import org.grails.web.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.proxy.cloudListener.CloudMQListener;

import static com.proxy.CloudMQ.MessageMetadata.HEARTBEAT;
import static com.proxy.CloudMQ.MessageMetadata.TOKEN;

public class CloudMQ {
    public enum MessageMetadata {
        HEARTBEAT("heartbeat"),
        REQUEST_RESPONSE("requestResponse"),
        TOKEN("token"),
        CONNECTION_CLOSED("connectionClosed");

        final String value;
        MessageMetadata(String value)  {
            this.value = value;
        }
    }
    private boolean running = false;
    private ExecutorService browserWriteExecutor = null;
    private ExecutorService browserReadExecutor = null;
    private ExecutorService sendToCloudProxyExecutor = null;
    private ScheduledExecutorService cloudConnectionCheckExecutor;

    final Map<Integer, SocketChannel> tokenSocketMap = new ConcurrentHashMap<>();

    private static final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    private Session cloudProxySession;
    private CloudProxyReaderWriter readerWriter;
    final private String productId;
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private final CloudMQInstanceMap instances;
    // Remove browser (nvrSessionId) references after 120 seconds with no call coming in.
    //  This is twice the time between getTemperature calls which will refresh the timer as they come in, as
    //  will any other new request from the browser,
    private final long browserSessionTimeout = 120 * 1000;

    final private Map<String, Timer> sessionCountTimers;
    SimpMessagingTemplate brokerMessagingTemplate;
    final String update = new JSONObject()
            .put("message", "update")
            .toString();


    public CloudMQ(Session session, String productId, CloudMQInstanceMap instances) {
        this.cloudProxySession = session;
        this.productId = productId;
        this.instances = instances;
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();
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
                readerWriter.setHeartbeatHandler(new HeartbeatHandler());
                startCloudProxyConnectionCheck();
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName()+" in CloudMQ.start: "+ex.getMessage());
            }
        }
    }

    public void stop() {
        try  {
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
    public interface IHandler {
        Object handler(Message msg);
    }
    private class CloudProxyReaderWriter implements MessageListener {
        private Destination cloudProxyQueue = null;
        private Destination cloudProxyResponseQueue;
        private  MessageConsumer cloudProxyConsumer = null;
        private MessageProducer cloudProxyProducer = null;
        private IHandler _heartbeatHandler = null;
        CloudProxyReaderWriter() {
            try {
                cloudProxyQueue = cloudProxySession.createQueue(productId);   // Create a queue with the NVR product id as the name
                cloudProxyResponseQueue = cloudProxySession.createTemporaryQueue();

                cloudProxyProducer = cloudProxySession.createProducer(cloudProxyQueue);
                cloudProxyProducer.setTimeToLive(1000);
                cloudProxyConsumer = cloudProxySession.createConsumer(cloudProxyResponseQueue);

                cloudProxyConsumer.setMessageListener(this);
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.constructor: " + ex.getMessage());
            }
        }
        void setHeartbeatHandler(IHandler value) {
            _heartbeatHandler = value;
        }
        void write(Message bm) {
             try {
                bm.setJMSCorrelationID("cloud");
                bm.setJMSReplyTo(cloudProxyResponseQueue);
                cloudProxyProducer.send(bm);
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.write: " + ex.getMessage());
            }
        }

        void stop() {
            try {
                // Close the consumer and producer in a thread as this may block
                Thread t = new Thread(()-> {
                    try {
                        cloudProxyConsumer.close();
                        cloudProxyProducer.close();
                    }
                    catch(Exception exception) {
                        logger.error(exception.getClass().getName()+" in CloudProxyReaderWriter.stop: "+exception.getMessage());
                    }
                });
                t.start();
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName() + " in CloudProxyReaderWriter.stop: " + ex.getMessage());
            }
        }

        @Override
        public void onMessage(Message message) {
            try {
                if (message instanceof BytesMessage bm) {
                    if(message.getBooleanProperty(HEARTBEAT.value)) {
                        logger.info("Heartbeat received");
                        if(_heartbeatHandler != null)
                            _heartbeatHandler.handler(message);
                    }
                    else if(Objects.equals(bm.getJMSCorrelationID(), "cloudProxy")) {
                        respondToBrowser(bm);
                    }
                    else
                        logger.warn("Received unexpected correlation ID "+bm.getJMSCorrelationID());
                }
                else {
                    logger.warn("Unhandled message type ("+message.getClass().getName()+" in CloudProxyReaderWriter.onMessage");
                }
            }
            catch(Exception ex) {
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
                try(final DatagramSocket socket = new DatagramSocket()){
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                    localIP = socket.getLocalAddress().getHostAddress();
                }                String payload = "username=" + cloudProperties.getUSERNAME() + "&password=" + cloudProperties.getPASSWORD();

                String output = "POST /login/authenticate HTTP/1.1\r\n" +
                        "Host: " + localIP + "\r\n" +
                        "DNT: 1\r\n" +
                        "Upgrade-Insecure-Requests: 1\r\n" +
                        "X-Auth-Token: 7yk=zJu+@77x@MTJG2HD*YLJgvBthkW!\r\n" +
                        "Content-type: application/x-www-form-urlencoded\r\n" +
                        "Content-Length: " + payload.length() + "\r\n\r\n" +
                        payload + "\r\n";

                System.out.print(output);
                Message response = doRequestResponse(output);

                if (response != null) {
                    HttpMessage hdrs = new HttpMessage(response);
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
                    System.out.print(((TextMessage)response).getText());
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
     * @param cookie: The NVR session cookie
     * @return: true on success
     */
    public boolean logoff(String cookie) {
        boolean retVal = true;
        if (cloudProxySession != null) {
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
                // Send the initial message delivered from the CloudMQListener
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
                BytesMessage bm = cloudProxySession.createBytesMessage();
                bm.writeBytes(buf.array(), 0, buf.limit());
                bm.setIntProperty("token", token);
                bm.setBooleanProperty("connectionClosed", true);
                sendRequestToCloudProxy(bm);  // Signal connection is closed
                removeSocket(token);
            } catch (IOException ignored) {
                removeSocket(token);
            } catch (Exception ex) {
                showExceptionDetails(ex, "readFromBrowser");
                // reset();
            }
        });
    }

    class HeartbeatHandler implements IHandler {
        public Object handler(Message ignore) {
            try {
                instances.resetNVRTimeout(getProductId());  // Reset the timeout which would otherwise  remove this Cloud instance from the map
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in handleHeartbeat: " + ex.getMessage());
            }
            return null;
        }
    }

    private void respondToBrowser(BytesMessage bm) {
        try {
             browserWriteExecutor.submit(() -> {
                try {
                    int token = bm.getIntProperty("token");
                    int length = (int)bm.getBodyLength();
                    SocketChannel frontEndChannel = tokenSocketMap.get(token);  //Select the correct connection to respond to
                    if (bm.getBooleanProperty("connectionClosed")) {
                        removeSocket(token);  // Usually already gone
                    } else if (frontEndChannel != null && frontEndChannel.isOpen()) {
                        ByteBuffer buf = ByteBuffer.allocate(length);
                        bm.readBytes(buf.array());
                        int result;
                        logger.debug("respondToBrowser length: " + length);

                        try {
                            do {
                                result = frontEndChannel.write(buf);
                            }
                            while (result != -1 && buf.position() < buf.limit());
                        } catch (IOException ioex) {
                            logger.warn("IOException in respondToBrowser: " + ioex.getMessage());
                            bm.setBooleanProperty("connectionClosed", true);
                            sendRequestToCloudProxy(bm);
                            frontEndChannel.shutdownOutput().close();
                        }
                    }
                } catch (Exception ex) {
                    showExceptionDetails(ex, "respondToBrowser");
                }
            });
        }
        catch(Exception ex) {
            logger.error(ex.getClass().getName()+" in respondToBrowser: "+ex.getMessage());
        }
    }
     private void removeSocket(int token) {
        try (var ignored = tokenSocketMap.remove(token)) {
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
     * getBuffer: Get a buffer and place the token at the start.
     *
     * @return: The byte buffer with the token in place and length reservation set up.
     */
    private ByteBuffer getBuffer() {
        final int BUFFER_SIZE = 1024;

        return ByteBuffer.allocate(BUFFER_SIZE);
    }

    /**
     * getToken: Get a sequential integer as a token
     *
     * @return: The token as an integer
     */
    private synchronized int getToken() {
        return CloudMQListener.get_nextToken();
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
                    if(running)
                        readerWriter.write(msg);
                 }  catch (Exception ex) {
                    logger.error(ex.getClass().getName()+" in cloudConnectionCheck: " + ex.getMessage());
                 //   restart();
                }
            }, 10, 10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            showExceptionDetails(ex, "startCloudConnectionCheck");
          //  restart();
        }
    }

    /**
     * doRequestResponse: Send a request to the webserver, and grab the response before it's sent to the web browser.
     * Return the response in its ByteBuffer.
     *
     * @param request: The request as a string
     * @return: The response in a ByteBuffer
     */
    private Message doRequestResponse(String request) {
        try {
            final int token = getToken();
            BytesMessage tm = cloudProxySession.createBytesMessage();
            tm.writeBytes(request.getBytes(), 0, request.length());
            Destination cloudProxy = cloudProxySession.createQueue(productId);
            MessageProducer cloudProxyProducer = cloudProxySession.createProducer(cloudProxy);
            cloudProxyProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            Destination replyTo = cloudProxySession.createTemporaryQueue();
            tm.setJMSReplyTo(replyTo);
            tm.setJMSCorrelationID("requestResponse");
            tm.setBooleanProperty("requestResponse", true);
            tm.setIntProperty(TOKEN.value, token);
            cloudProxyProducer.send(tm);
            MessageConsumer fromCloudProxy = cloudProxySession.createConsumer(replyTo);
            return fromCloudProxy.receive(1000);
        }
        catch(Exception ex) {
            logger.error(ex.getClass().getName()+" in doRequestResponse: "+ex.getMessage());
        }
        return null;
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
            BytesMessage bm = cloudProxySession.createBytesMessage();
            bm.writeBytes(buf.array(), 0, buf.limit());
            bm.setIntProperty("token", token);
            logger.debug("readFromBrowser length: " + buf.limit());
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

