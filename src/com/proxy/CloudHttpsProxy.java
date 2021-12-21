package com.proxy;


import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import org.apache.commons.lang3.ArrayUtils;

import javax.net.ssl.*;
import java.io.*;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static com.proxy.SslUtil.*;

public class CloudHttpsProxy implements SslContextProvider {
    public static void main(String[] args) throws IOException {
        try {
            new CloudHttpsProxy().runServer("localhost", 8083, 8082);
        } catch (Exception e) {
            System.err.println(e); //Prints the standard errors
        }
    }

    private static final Logger logger = Logger.getLogger("CloudProxy");
    private final int threadPoolSize = 20;
    private final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(threadPoolSize);

    static String JSESSIONID = "";
    static String baseUrl;

    String authenticate(SSLSocket socket) {
        try {
            OutputStream os = socket.getOutputStream();
            InputStream is = socket.getInputStream();
            byte[] buf = new byte[500];
            socket.setReceiveBufferSize(buf.length);
            //System.out.printf("Connected to server (%s). Writing ping...%n", getPeerIdentity(socket));

            String payload = "username=" + RestfulProperties.USERNAME + "&password=" + RestfulProperties.PASSWORD;

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

            int bytesRead = is.read(buf);
            HttpMessage hdrs = new HttpMessage(buf, bytesRead);
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
                ;
            }

            String response = new String(buf);
            System.out.print(response);

        } catch (Exception ex) {
            System.out.println("Exception in authenticate: " + ex.getMessage());
        }

        return JSESSIONID;
    }

    /**
     * It will run a single-threaded proxy server on
     * the provided local port.
     */
    public void runServer(String host, int remoteport, int localport)
            throws IOException {
        // Creating a ServerSocket to listen for connections with
        ServerSocket s = new ServerSocket(localport);
        while (true) {
            Socket client = null;

            try {
                // It will wait for a connection on the local port
                client = s.accept();


                requestProcessing(client, host, remoteport);
            } catch (Exception ex) {

            }
        }
    }

    void requestProcessing(@NotNull Socket client, String host, int remoteport) {
        threadPoolExecutor.execute(() -> handleClientRequest(client, host, remoteport));
    }

    private void handleClientRequest(@NotNull Socket client, String host, int remoteport) {
        SSLSocket server = null;
        try {
            final byte[] request = new byte[1024];
            final AtomicReference<byte[]> remainsOfPreviousMessage = new AtomicReference<byte[]>(null);
            byte[] reply = new byte[4096];

            final InputStream streamFromClient = client.getInputStream();
            final OutputStream streamToClient = client.getOutputStream();

            // Create a connection to the real server.
            // If we cannot connect to the server, send an error to the
            // client, disconnect, and continue waiting for connections.
            try {
                server = createSSLSocket(host, remoteport);
                if (Objects.equals(JSESSIONID, ""))
                    authenticate(server);
            } catch (IOException e) {
                PrintWriter out = new PrintWriter(streamToClient);
                out.print("Proxy server cannot connect to " + host + ":"
                        + remoteport + ":\n" + e + "\n");
                out.flush();
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Get server streams.
            assert server != null;
            final InputStream streamFromServer = server.getInputStream();
            final OutputStream streamToServer = server.getOutputStream();

            // a thread to read the client's requests and pass them
            // to the server. A separate thread for asynchronous.
            final SSLSocket finalServer = server;
            threadPoolExecutor.execute(() -> {
                int bytesRead;
                try {
                    while ((bytesRead=streamFromClient.read(request)) != -1) {
                        splitMessages(request, bytesRead, streamToServer, remainsOfPreviousMessage);
                    }
                } catch (IOException e) {
                }

                // the client closed the connection to us, so close our
                // connection to the server.
                try {
                    streamToServer.close();
                } catch (IOException e) {
                }
            });

            // Read the server's responses
            // and pass them back to the client.
            int bytesRead;
            try {
                while ((bytesRead = streamFromServer.read(reply)) != -1) {
                    streamToClient.write(reply, 0, bytesRead);
                    streamToClient.flush();
                }
            } catch (IOException e) {
            }
            // The server closed its connection to us, so we close our
            // connection to our client.
            streamToClient.close();
        } catch (IOException e) {
            System.err.println(e);
        } finally {
            try {
                if (server != null)
                    server.close();
                if (client != null)
                    client.close();
            } catch (IOException e) {
            }
        }
    }

    void splitMessages(final byte[] buf, final int bytesRead, final OutputStream os, final AtomicReference<byte[]> remainsOfPreviousMessage) {
        byte[] workBuf = remainsOfPreviousMessage.get() == null ? Arrays.copyOfRange(buf, 0, bytesRead) :
                ArrayUtils.addAll(remainsOfPreviousMessage.get(), Arrays.copyOfRange(buf, 0, bytesRead));
        int startIndex = 0;
        final int workBufInitialLength = workBuf.length;

        while (startIndex < workBuf.length) {
            final HttpMessage msg = new HttpMessage(workBuf, workBuf.length);
            if (!msg.headersBuilt) {
                // Don't know what this message is, just send it
                remainsOfPreviousMessage.set(null);
                try {
                    os.write(workBuf, 0, workBuf.length);
                    os.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            String hdrs =  msg.getHeaders();
            int headersLength = hdrs.length();
            int messageLength = headersLength + msg.getContentLength();

            if(startIndex+messageLength <= workBuf.length) {
                List<String> js = new ArrayList<String>();
                js.add("JSESSIONID=" + JSESSIONID);
                msg.put("Cookie", js);

                try {
                    String headers = msg.getHeaders();
                    os.write(headers.getBytes(StandardCharsets.UTF_8), 0, headers.length());
                    if (msg.getContentLength() > 0)
                        os.write(msg.getMessageBody(), 0, msg.getContentLength());
                    os.flush();
                } catch (Exception ex) {
                    System.out.println("ERROR: Exception in splitMessage when writing to stream: " + ex.getMessage());
                }
                startIndex += messageLength;
                remainsOfPreviousMessage.set(null);
            }
            else {
                remainsOfPreviousMessage.set(Arrays.copyOfRange(workBuf, startIndex, workBuf.length));
                break;
            }
        }
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

