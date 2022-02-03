package com.proxy;

import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.proxy.SslUtil.*;

public class CloudListener implements SslContextProvider {
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private boolean allRunning = false;
    final private int cloudProxyFacingPort = 8081;
    private ExecutorService acceptConnectionsFromCloudProxyExecutor = null;
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    final private CloudInstanceMap instances = new CloudInstanceMap();

    private SSLServerSocket _s = null;

    public void start()
    {
        if(!allRunning) {
            acceptConnectionsFromCloudProxyExecutor= Executors.newSingleThreadExecutor();
            allRunning = true;
            acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
        }
    }

    public void stop()
    {
        try {
            // Stop the CloudProxy listener threads
            allRunning = false;
            acceptConnectionsFromCloudProxyExecutor.shutdownNow();

            // Close the CloudProxy listening socket
            if (_s != null)
                _s.close();

            //  Close all the Cloud instances
            instances.forEach((prodId ,cloud) -> {
                cloud.stop();
            });

        }
        catch(Exception ex)
        {
            logger.error(ex.getClass().getName()+" when closing CloudProxy listening socket: "+ex.getMessage());
        }
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
                            cloudProxy.setSoTimeout(0);
                            cloudProxy.setSoLinger(true, 5);
                            String prodId = getProductId(cloudProxy);
                            if(!Objects.equals(prodId, "")) {
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
                    logger.error(ioex.getClass().getName()+" in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                }
            }
        });
    }

    private void startNewInstance(SSLSocket cloudProxy, String prodId) {
        if(!instances.containsProductKey(prodId)) {
            Cloud newInstance = new Cloud(cloudProxy, prodId);
            newInstance.start();
            instances.putByProductId(prodId, newInstance);
        }
        else
            instances.getByProductId(prodId).setCloudProxyConnection(cloudProxy);
    }

    private String getProductId(SSLSocket cloudProxy) {
        String retVal = "";
        try {
            InputStream is = cloudProxy.getInputStream();
            byte[] prodId = new byte[1024];
            int bytesRead = is.read(prodId);

            if(bytesRead != -1)
            {
                AsymmetricCryptography ac = new AsymmetricCryptography();
                PrivateKey privateKey = ac.getPrivate(cloudProperties.getPRIVATE_KEY_PATH());
                String encryptedId = new String(prodId);
                String decryptedId = ac.decryptText(encryptedId, privateKey);
                if(decryptedId.matches("^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}$")) {
                    retVal = decryptedId;

                    // Send affirmative response to CloudProxy
                    OutputStream os = cloudProxy.getOutputStream();
                    os.write("OK".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        }
        catch(Exception ex)
        {
            logger.error(ex.getClass().getName()+" in getProductId: "+ex.getMessage());
        }
        if(retVal.equals("")) {
            try {
                // Send error response to CloudProxy
                OutputStream os = cloudProxy.getOutputStream();
                os.write("ERROR".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            catch(IOException ex)
            {
                logger.error(ex.getClass().getName()+" in getProductId when sending error response: "+ex.getMessage());
            }
        }

        return retVal;
    }

    /**
     * authenticate: Authenticate via the appropriate Cloud instance
     * @param productId: The users product id
     * @return: The NVRSESSIONID
     */
    public String authenticate(String productId) {
        if(instances.containsProductKey(productId)) {
            Cloud inst = instances.getByProductId(productId);
            String nvrSessionId = inst.authenticate();
            if(nvrSessionId != "" && instances.getBySessionId(nvrSessionId) == null)
                instances.putBySessionId(nvrSessionId, inst);

            return nvrSessionId;
        }

        return "";
    }

    public void logoff(String cookie)
    {
        try {
            final int startIndex = cookie.indexOf("NVRSESSIONID");
            int endIndex = cookie.indexOf(";", startIndex);
            endIndex = endIndex == -1 ? cookie.length() : endIndex;
            final String nvrSessionId = cookie.substring(startIndex + "NVRSESSIONID=".length(), endIndex);
            final String cookieDef = cookie.substring(startIndex, endIndex);
            final Cloud inst = instances.getBySessionId(nvrSessionId);
            inst.logoff(cookieDef);
            inst.stop();
            instances.removeBySessionId(nvrSessionId);
        }
        catch(Exception ex)
        {
            logger.error(ex.getClass().getName()+" in logoff: "+ex.getMessage());
        }
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
