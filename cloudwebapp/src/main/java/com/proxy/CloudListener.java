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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.proxy.SslUtil.*;

public class CloudListener implements SslContextProvider {
    private final CloudProperties cloudProperties = CloudProperties.getInstance();
    private boolean allRunning = false;
    final private int cloudProxyFacingPort = 8081;
    private final ExecutorService acceptConnectionsFromCloudProxyExecutor = Executors.newSingleThreadExecutor();
    private final Logger logger = (Logger) LoggerFactory.getLogger("CLOUD");
    final private Map<String, Cloud> instances = new ConcurrentHashMap<>();

    private SSLServerSocket _s = null;

    public void start()
    {
        if(!allRunning) {
            allRunning = true;
            acceptConnectionsFromCloudProxy(cloudProxyFacingPort);
        }
    }

    public void stop()
    {
        try {
            // Close the Cloud|Proxy listening socket
            if (_s != null)
                _s.close();
        }
        catch(IOException ex)
        {
            logger.error("Exception when closing CloudProxy listening socket: "+ex.getMessage());
        }

        acceptConnectionsFromCloudProxyExecutor.shutdownNow();
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
                    }
                } catch (Exception ioex) {
                    logger.error("Exception in acceptConnectionsFromCloudProxy: " + ioex.getClass().getName() + ": " + ioex.getMessage());
                }
            }
        });
    }

    private void startNewInstance(SSLSocket cloudProxy, String prodId) {
        if(!instances.containsKey(prodId)) {
            Cloud newInstance = new Cloud(cloudProxy, prodId);
            instances.put(prodId, newInstance);
        }
        else
            instances.get(prodId).setCloudProxyConnection(cloudProxy);
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
            logger.error("Exception in getProductId: "+ex.getMessage());
        }
        if(retVal == "") {
            try {
                // Send error response to CloudProxy
                OutputStream os = cloudProxy.getOutputStream();
                os.write("ERROR".getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
            catch(IOException ex)
            {
                logger.error("Exception in getProductId when sending error response: "+ex.getMessage());
            }
        }

        return retVal;
    }

    /**
     * authenticate: Authenticate via the appropriate Cloud instance
     * @param productId: The users product id
     * @return
     */
    public String authenticate(String productId) {
        if(instances.containsKey(productId))
            return instances.get(productId).authenticate();

        return "";
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
