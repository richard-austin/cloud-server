package com.proxy;


import com.sun.net.httpserver.*;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import static com.proxy.SslUtil.*;

public class CloudHttpsProxy implements SslContextProvider {
    public static void main(String[] args) throws IOException {
        try {
            new CloudHttpsProxy().authenticate("192.168.0.29", 443);
        } catch (Exception e) {
            System.err.println(e); //Prints the standard errors
        }
    }

    private static final Logger logger = Logger.getLogger("CloudProxy");
    private final int threadPoolSize = 20;
    private final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadPoolSize);

    static String JSESSIONID;
    static String baseUrl;

    void runServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8001), 0);
          //  server.createContext("/test", new HttpRequestHandler());
            server.setExecutor(threadPoolExecutor);
            server.start();
            logger.info(" Server started on port 8001");
        } catch (Exception ex) {
            System.out.println("Exception in runServer: " + ex.getMessage() + "\n" + Arrays.toString(ex.getStackTrace()));
        }
    }

    String authenticate(String host, int port) {
        try {
            SSLSocket socket = createSSLSocket(host, port);
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[500];
            socket.setReceiveBufferSize(buf.length);
            //System.out.printf("Connected to server (%s). Writing ping...%n", getPeerIdentity(socket));

            String payload = "username="+RestfulProperties.USERNAME+"&password="+RestfulProperties.PASSWORD;

            String output = "POST /login/authenticate HTTP/1.1\r\n" +
                    "Host: host\r\n" +
                    "DNT: 1\r\n" +
                    "Upgrade-Insecure-Requests: 1\r\n" +
                    "Content-type: application/x-www-form-urlencoded\r\n" +
                    "Content-Length: " + payload.length() + "\r\n\r\n" +
                    payload + "\r\n";
            System.out.print(output);
            os.write(output.getBytes(StandardCharsets.UTF_8));
            os.flush();

            int read = is.read(buf);
            HttpMessage hdrs = new HttpMessage(buf);
            hdrs.buildHeaders();

            var l = hdrs.getHeader("Location");
            String location = l.size() == 1 ? l.get(0) : null;
            if(Objects.equals(location, "/")) {
                List<String> setCookie = hdrs.getHeader("Set-Cookie");
                for(String cookie: setCookie) {
                    if(cookie.startsWith("JSESSIONID"))
                    {
                        JSESSIONID=cookie.substring(11, 43);
                        break;
                    }
                    else
                        JSESSIONID = "";
                };
            }

            String response = new String(buf);
            System.out.print(response);

        } catch (Exception ex) {
            System.out.println("Exception in authenticate: " + ex.getMessage());
        }

        return JSESSIONID;
    }

    private SSLSocket createSSLSocket(String host, int port) throws Exception {
        return SslUtil.createSSLSocket(host, port, this);
    }

    @Override
    public KeyManager[] getKeyManagers() throws GeneralSecurityException, IOException {
        return createKeyManagers(RestfulProperties.KEYSTORE_PATH, RestfulProperties.KEYSTORE_PASSWORD.toCharArray());
    }

    @Override
    public String getProtocol() {
        return "TLSv1.2";
    }

    @Override
    public TrustManager[] getTrustManagers() throws GeneralSecurityException, IOException {
        return createTrustManagers(RestfulProperties.TRUSTSTORE_PATH, RestfulProperties.TRUSTSTORE_PASSWORD.toCharArray());
    }
}

