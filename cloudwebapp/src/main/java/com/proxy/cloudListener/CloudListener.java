package com.proxy.cloudListener;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.proxy.*;
import org.slf4j.LoggerFactory;

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
import java.security.PrivateKey;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

import static com.proxy.SslUtil.*;

public class CloudListener implements SslContextProvider {
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private boolean allRunning = false;
    final private static Queue<ByteBuffer> bufferQueue = new ConcurrentLinkedQueue<>();
    public static final int BUFFER_SIZE = 1024;
    private final static int tokenLength = Integer.BYTES;

    private ExecutorService acceptConnectionsFromCloudProxyExecutor = null;
    private ExecutorService browserReadExecutor = null;
    private ExecutorService acceptConnectionsFromBrowserExecutor = null;

    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    final private CloudInstanceMap instances = new CloudInstanceMap();
    private static int _nextToken = 0;
    private SSLServerSocket _s = null;
    private ServerSocketChannel _sc = null;

    public void start() {
        logger.setLevel(Level.INFO);
        int cloudProxyFacingPort;
        int browserFacingPort;

        if (!allRunning) {
            setLogLevel(cloudProperties.getLOG_LEVEL());
            browserFacingPort = cloudProperties.getBROWSER_FACING_PORT();
            cloudProxyFacingPort = cloudProperties.getCLOUD_PROXY_FACING_PORT();

            acceptConnectionsFromCloudProxyExecutor = Executors.newSingleThreadExecutor();
            acceptConnectionsFromBrowserExecutor = Executors.newSingleThreadExecutor();
            browserReadExecutor = Executors.newCachedThreadPool();
            allRunning = true;
            acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
            acceptConnectionsFromBrowser(browserFacingPort);
        }
    }

    public void stop() {
        try {
            // Stop the CloudProxy and browser listener threads
            allRunning = false;
            acceptConnectionsFromCloudProxyExecutor.shutdownNow();
            acceptConnectionsFromBrowserExecutor.shutdownNow();
            browserReadExecutor.shutdownNow();

            // Close the CloudProxy listening socket
            if (_s != null)
                _s.close();

            if (_sc != null)
                _sc.close();

            //  Close all the Cloud instances
            instances.forEach((ignore, cloud) -> cloud.stop());

        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " when closing CloudProxy listening socket: " + ex.getMessage());
        }
    }

    /**
     * getSessions: Gets the number of currently active sessions against product ID
     *
     * @return: Map of number of sessions by product ID
     */
    public Map<String, Cloud> getInstances() {
        return instances.map;
    }

    private void acceptConnectionsFromCloudProxy(final int cloudProxyFacingPort) {
        acceptConnectionsFromCloudProxyExecutor.execute(() -> {
            while (allRunning) {
                try {
                    // Creating a ServerSocket to listen for connections with
                    SSLServerSocket s = _s = createSSLServerSocket(cloudProxyFacingPort, this);
                    while (allRunning) {
                        try {
                            // It will wait for a connection on the local port
                            SSLSocket cloudProxy = (SSLSocket) s.accept();
                            logger.info("Accepted connection from NVR at " + cloudProxy.getRemoteSocketAddress().toString());
                            cloudProxy.setSoTimeout(0);
                            cloudProxy.setSoLinger(true, 5);
                            String prodId = getProductId(cloudProxy);
                            if (!Objects.equals(prodId, "")) {
                                startNewInstance(cloudProxy, prodId);
                            }

//                            if (!protocolAgnostic)
//                                authenticate();
                        } catch (IOException ioex) {
                            logger.error("IOException in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                        }
                        synchronized (acceptConnectionsFromCloudProxyExecutor) {
                            // Wait 10 milliseconds to prevent 100% CPU in case of repeat exceptions
                            acceptConnectionsFromCloudProxyExecutor.wait(10);
                        }
                    }
                } catch (Exception ioex) {
                    logger.error(ioex.getClass().getName() + " in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                }
            }
        });
    }

    public static synchronized int get_nextToken() {
        return ++_nextToken;
    }

    private void startNewInstance(SSLSocket cloudProxy, String prodId) {
        if (!instances.containsKey(prodId)) {
            Cloud newInstance = new Cloud(cloudProxy, prodId, instances);
            newInstance.start();
            instances.put(prodId, newInstance);
        } else {
            instances.resetNVRTimeout(prodId);
            Cloud cloud = instances.get(prodId);
            cloud.setCloudProxyConnection(cloudProxy);
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
                        Cloud inst = instances.get(PRODUCTID);
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

    private String getProductId(SSLSocket cloudProxy) {
        String retVal = "";
        try {
            InputStream is = cloudProxy.getInputStream();
            byte[] prodId = new byte[1024];
            int bytesRead = is.read(prodId);

            if (bytesRead != -1) {
                AsymmetricCryptography ac = new AsymmetricCryptography();
                PrivateKey privateKey = ac.getPrivate(cloudProperties.getPRIVATE_KEY_PATH());
                String encryptedId = new String(prodId);
                String decryptedId = ac.decryptText(encryptedId, privateKey);
                if (decryptedId.matches("^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$")) {
                    retVal = decryptedId;

                    // Send affirmative response to CloudProxy
                    OutputStream os = cloudProxy.getOutputStream();
                    os.write("OK".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        } catch (Exception ex) {
            logger.error(ex.getClass().getName() + " in getProductId: " + ex.getMessage());
        }
        if (retVal.equals("")) {
            try {
                // Send error response to CloudProxy
                OutputStream os = cloudProxy.getOutputStream();
                os.write("ERROR".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ex) {
                logger.error(ex.getClass().getName() + " in getProductId when sending error response: " + ex.getMessage());
            }
        }

        return retVal;
    }

    /**
     * authenticate: Authenticate via the appropriate Cloud instance
     *
     * @param productId: The users product id
     * @return: The NVRSESSIONID
     */
    public String authenticate(String productId) {
        if (instances.containsKey(productId)) {
            Cloud inst = instances.get(productId);
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


            final Cloud inst = instances.get(productid);
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

    public static void setToken(ByteBuffer buf, int token) {
        buf.position(0);
        buf.putInt(token);
        buf.putInt(0);  // Reserve space for the data length
        buf.put((byte) 0); // Reserve space for the closed connection flag
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

    /**
     * setDataLength: Set the lengthLength bytes following the token to the length of the data in the buffer
     * (minus token and length bytes).
     *
     * @param buf:    The buffer to set the length in.
     * @param length: The length to set.
     */
    public static void setDataLength(ByteBuffer buf, int length) {
        int position = buf.position();
        buf.position(tokenLength);
        buf.putInt(length);
        buf.position(position);
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
}
