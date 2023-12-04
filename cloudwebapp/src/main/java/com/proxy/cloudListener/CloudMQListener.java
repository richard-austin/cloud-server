package com.proxy.cloudListener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.proxy.AsymmetricCryptography;
import com.proxy.CloudMQ;
import com.proxy.CloudProperties;
import com.proxy.HttpMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.activemq.transport.TransportListener;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

public class CloudMQListener {
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    final private static Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    public static final int BUFFER_SIZE = 1024;
    final private CloudMQInstanceMap instances = new CloudMQInstanceMap();
    private ExecutorService browserReadExecutor = null;
    private ExecutorService acceptConnectionsFromBrowserExecutor = null;

    private static int _nextToken = 0;
    private ServerSocketChannel _sc = null;
    private boolean allRunning = false;
    private class InitQueueConsumer implements MessageListener, ExceptionListener {
        ActiveMQConnection connection = null;
        Session session = null;
        MessageConsumer consumer = null;
        boolean running = false;
        void start() {
            try {
                if(!running) {
                    running = true;
                    int browserFacingPort;

                ActiveMQSslConnectionFactory connectionFactory = new ActiveMQSslConnectionFactory("failover://ssl://localhost:61617?socket.verifyHostName=false");
                    connectionFactory.setKeyStore("/home/richard/client.ks");
                    connectionFactory.setKeyStorePassword("password");
                    connectionFactory.setTrustStore("/home/richard/client.ts");
                    connectionFactory.setTrustStorePassword("password");

                    browserFacingPort = cloudProperties.getBROWSER_FACING_PORT();
                    connection = (ActiveMQConnection) connectionFactory.createConnection();

                    TransportListener tl = new TransportListener() {
                        @Override
                        public void onCommand(Object command) {
                            //   logger.info("Command");
                        }

                        @Override
                        public void onException(IOException error) {
                            logger.info(error.getClass().getName()+" received in InitQueueConsumer transport listener: "+error.getMessage());
                        }

                        @Override
                        public void transportInterupted() {
                            logger.info("Transport interrupted");
                            instances.clear();
                        }

                        @Override
                        public void transportResumed() {
                            logger.info("Transport resumed");
                        }
                    };
                    connection.addTransportListener(tl);
                    connection.start();
                    connection.setExceptionListener(this);
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    Destination dest = session.createQueue("INIT");
                    consumer = session.createConsumer(dest);
                    consumer.setMessageListener(this);
                    allRunning = true;
                    acceptConnectionsFromBrowser(browserFacingPort);
                }
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName()+" in InitQueueConsumer.start: "+ex.getMessage());
            }
        }

        void stop() {
            try {
                if(running) {
                    running = false;
                    session.close();
                    connection.stop();
                    connection.close();
                    if (_sc != null)
                        _sc.close();
                }
            }
            catch(Exception ex) {
                logger.error(ex.getClass().getName()+ " in InitQueueConsumer.stop(): "+ex.getMessage());
            }
        }

        @Override
        public void onMessage(Message message) {
            String productId = getProductId(message);
            try {
                String productIdRegex = "^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$";
                if (productId.matches(productIdRegex)) {
                    logger.info("Connection from NVR "+productId);
                    startNewInstance(session, productId);
                }
                Destination replyQ = message.getJMSReplyTo();
//                String correlationId = message.getJMSCorrelationID();
                MessageProducer producer = session.createProducer(replyQ);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                TextMessage tMsg = session.createTextMessage();
                tMsg.setText(productId);
                tMsg.setJMSCorrelationID("initResponseCorrelationId");
                tMsg.setBooleanProperty("INIT_RESPONSE", true);
                producer.send(tMsg);
            }
            catch(JMSException ex) {
                logger.error(ex.getClass().getName()+" in CloudMQListener.onMessage: "+ex.getMessage());
            }
        }

        @Override
        public void onException(JMSException exception) {
            logger.error(exception.getClass().getName()+ " in InitQueueConsumer: "+exception.getMessage());
            stop();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            start();
        }
    }

    InitQueueConsumer consumer = null;
    public void start() {
        logger.setLevel(Level.INFO);
        if (!allRunning) {
            setLogLevel(cloudProperties.getLOG_LEVEL());
            allRunning = true;
            consumer = new InitQueueConsumer();
            consumer.start();
            browserReadExecutor = Executors.newCachedThreadPool();
            acceptConnectionsFromBrowserExecutor = Executors.newSingleThreadExecutor();
        }
    }

    public void stop() {
        if(allRunning) {
            allRunning = false;
            consumer.stop();
            browserReadExecutor.shutdownNow();
            acceptConnectionsFromBrowserExecutor.shutdownNow();
        }
    }

    private String getProductId(Message message) {
        String retVal = "";
            if (message instanceof BytesMessage) {
                try {
                byte[] prodId = new byte[1024];
                int bytesRead;
                BytesMessage bm = (BytesMessage) message;
                bytesRead = bm.readBytes(prodId);
                if (bytesRead > 0) {
                    AsymmetricCryptography ac = new AsymmetricCryptography();
                    PrivateKey privateKey = ac.getPrivate(cloudProperties.getPRIVATE_KEY_PATH());
                    String encryptedId = new String(prodId);
                    String decryptedId = ac.decryptText(encryptedId, privateKey);
                    if (decryptedId.matches("^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$")) {
                        retVal = decryptedId;
                    }
                }
            } catch(Exception ex){
                logger.error(ex.getClass().getName() + " in getProductId: " + ex.getMessage());
            }

        }
         return retVal;
    }

    public static synchronized int get_nextToken() {
        return ++_nextToken;
    }

    private void startNewInstance(Session session, String prodId) {
        if (!instances.containsKey(prodId)) {
            CloudMQ newInstance = new CloudMQ(session, prodId, instances);
            newInstance.start();
            instances.put(prodId, newInstance);
        } else {
            instances.resetNVRTimeout(prodId);
            CloudMQ cloud = instances.get(prodId);
            cloud.setCloudProxyConnection(session);
        }
    }

    /**
     * acceptConnectionsFromBrowser: Wait for connections from browser and read requests
     *
     * @param browserFacingPort: The port the browser connects to
     */
    private void acceptConnectionsFromBrowser(final int browserFacingPort) {

        acceptConnectionsFromBrowserExecutor.execute(() -> {
            while (allRunning) {
                try {
                    // Creating a ServerSocket to listen for connections with
                    ServerSocketChannel s = _sc = ServerSocketChannel.open();
                    s.bind(new InetSocketAddress(browserFacingPort));
                    while (allRunning) {
                        try {
                            // It will wait for a connection on the local port
                            SocketChannel browser = s.accept();
                            browser.configureBlocking(true);
                            final int token = get_nextToken();
                            readFromBrowser(browser, token);
                        } catch (Exception ex) {
                            logger.error(ex.getClass().getName() + " in acceptConnectionsFromBrowser:  " + ex.getMessage());
                        }
                    }
                } catch (IOException ioex) {
                    logger.error(ioex.getClass().getName() + " in acceptConnectionsFromBrowser: " + ioex.getMessage());
                }
            }
        });
    }

    private final String productIdRegex = "^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$";

    final void readFromBrowser(SocketChannel channel, final int token) {
        final BiFunction<String, String, String> cookieFinder = (String cookie, String key) -> {
            final int idx = cookie.indexOf(key) + (key + "=").length();
            int idx2 = cookie.indexOf(';', idx);
            if (idx2 == -1)
                idx2 = cookie.length();
            return cookie.substring(idx, idx2);
        };
        browserReadExecutor.submit(() -> {
            ByteBuffer buf = getBuffer();
            try {
                logger.debug("In readFromBrowser: token " + token);
                final int bytesRead = channel.read(buf);
                logger.debug("Read " + bytesRead + " token " + token);
                if (bytesRead > 0) {
                    HttpMessage msg = new HttpMessage(buf);
                    List<String> cookies = msg.get("cookie");
                    String PRODUCTID = "";
                    String NVRSESSIONID = "";
                    final String key = "PRODUCTID";
                    final String sessionIdKey = "NVRSESSIONID";
                    for (String cookie : cookies) {
                        if (cookie.contains(key))
                            PRODUCTID = cookieFinder.apply(cookie, key);
                        if (cookie.contains(sessionIdKey))
                            NVRSESSIONID = cookieFinder.apply(cookie, sessionIdKey);
                    }
                    if (PRODUCTID.matches(productIdRegex)) {
                        CloudMQ inst = instances.get(PRODUCTID);
                        if (inst != null)
                            // Call readFromBrowser on the Cloud instance if there is one for this session ID
                            inst.readFromBrowser(channel, buf, token, NVRSESSIONID);
                        else {
                            recycle(buf);  // No Cloud instance for this product ID, just ignore this message
                            sendErrorResponseToBrowser("No Cloud instance for this product ID", channel);
                        }
                    } else {
                        recycle(buf);  // No product ID, just ignore this message
                        sendErrorResponseToBrowser("Couldn't find Product ID", channel);
                    }
                } else
                    sendErrorResponseToBrowser("Channel closed", channel);

            } catch (IOException ignored) {
                //removeSocket(token);
            } catch (Exception ex) {
                logger.error(ex.getClass().getName() + " in readFromBrowser: " + ex.getMessage());
            }
        });
    }

    /**
     * sendErrorResponseToBrowser: Returns an error message to the browser where no response was received from the NVR
     * @param message: The error message to send
     * @param channel: The SocketChannel to send it on
     */
    private void sendErrorResponseToBrowser(final String message, SocketChannel channel) {
        String dateString = new Date().toString();

        final String resp =
                "HTTP/1.1 401 Unauthorized\n" +
                        "X-Powered-By: Express\n" +
                        "Access-Control-Allow-Origin: *\n" +
                        "vary: Origin, Access-Control-Request-Method, Access-Control-Request-Headers\n" +
                        "content-length: "+message.length()+"\n" +
                        "date: "+dateString+"\n" +
                        "connection: close\n\n"+
                        message +"\n";
        ByteBuffer buf = getBuffer();
        buf.put(resp.getBytes());
        buf.flip();
        try(SocketChannel c = channel)
        {
            c.write(buf);
        }
        catch(Exception ex)
        {
            logger.error(ex.getClass().getName() + " in sendErrorResponseToBrowser: " + ex.getMessage());
        }
    }

    /**
     * authenticate: Authenticate via the appropriate Cloud instance
     *
     * @param productId: The users product id
     * @return: The NVRSESSIONID
     */
    public String authenticate(String productId) {
        if (instances.containsKey(productId)) {
            CloudMQ inst = instances.get(productId);
            String nvrSessionId = inst.authenticate();
            inst.incSessionCount(nvrSessionId);

            return nvrSessionId;
        }

        return "";
    }

    public void logoff(String cookie) {
        try {
            int startIndex = cookie.indexOf("NVRSESSIONID");
            int endIndex = cookie.indexOf(";", startIndex);
            endIndex = endIndex == -1 ? cookie.length() : endIndex;
            startIndex += "NVRSESSIONID=".length();
            final String nvrSessionId = cookie.substring(startIndex, endIndex);

            final int piStartIndex = cookie.indexOf("PRODUCTID");
            int piEndIndex = cookie.indexOf(";", piStartIndex);
            piEndIndex = piEndIndex == -1 ? cookie.length() : piEndIndex;
            final String productid = cookie.substring(piStartIndex + "PRODUCTID=".length(), piEndIndex);


            final CloudMQ inst = instances.get(productid);
            inst.decSessionCount(nvrSessionId);
            inst.logoff(nvrSessionId);
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in logoff: " + ex.getMessage());
        }
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
    void setLogLevel(String level) {
        logger.setLevel(Objects.equals(level, "INFO") ? Level.INFO :
                Objects.equals(level, "DEBUG") ? Level.DEBUG :
                        Objects.equals(level, "TRACE") ? Level.TRACE :
                                Objects.equals(level, "WARN") ? Level.WARN :
                                        Objects.equals(level, "ERROR") ? Level.ERROR :
                                                Objects.equals(level, "OFF") ? Level.OFF :
                                                        Objects.equals(level, "ALL") ? Level.ALL : Level.OFF);
    }

}
