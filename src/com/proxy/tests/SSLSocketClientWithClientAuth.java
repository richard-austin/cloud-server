package com.proxy.tests;

import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import javax.security.cert.X509Certificate;
import java.security.KeyStore;

/*
 * This example shows how to set up a key manager to do client
 * authentication if required by server.
 *
 * This program assumes that the client is not inside a firewall.
 * The application can be modified to connect to a server outside
 * the firewall by following SSLSocketClientWithTunneling.java.
 */
public class SSLSocketClientWithClientAuth {

    public static void main(String[] args) throws Exception {
        String host = null;
        int port = -1;
        String path = null;
        for (int i = 0; i < args.length; i++)
            System.out.println(args[i]);

        if (args.length < 3) {
            System.out.println(
                    "USAGE: java SSLSocketClientWithClientAuth " +
                            "host port requestedfilepath");
            System.exit(-1);
        }

        try {
            host = args[0];
            port = Integer.parseInt(args[1]);
            path = args[2];
        } catch (IllegalArgumentException e) {
            System.out.println("USAGE: java SSLSocketClientWithClientAuth " +
                    "host port requestedfilepath");
            System.exit(-1);
        }

        try {

            /*
             * Set up a key manager for client authentication
             * if asked by the server.  Use the implementation's
             * default TrustStore and secureRandom routines.
             */
            SSLSocketFactory factory = null;
            try {
                SSLContext ctx;
                KeyManagerFactory kmf;
                KeyStore ks;
                char[] passphrase = "@lbuquerq".toCharArray();

                ctx = SSLContext.getInstance("TLS");
                kmf = KeyManagerFactory.getInstance("SunX509");
                ks = KeyStore.getInstance("JKS");

                ks.load(new FileInputStream("/home/richard/cloud.jks"), passphrase);

                kmf.init(ks, passphrase);
                ctx.init(kmf.getKeyManagers(), null, null);

                factory = ctx.getSocketFactory();
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }

            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

            /*
             * send http request
             *
             * See SSLSocketClient.java for more information about why
             * there is a forced handshake here when using PrintWriters.
             */
            socket.startHandshake();

            PrintWriter out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream())));
            out.println("GET " + path + " HTTP/1.1");
            out.println("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
            out.println("Accept-Encoding: gzip, deflate, br");
            out.println("Accept-Language: en-GB,en-US;q=0.9,en;q=0.8");
            out.println("Connection: keep-alive");
 //           out.println("Cookie: locale-name=en_US; JSESSIONID=E79EEACD9C730B8E787CCDF34D80725F.mash4; OptanonConsent=isIABGlobal=false&datestamp=Sat+Dec+18+2021+20%3A17%3A51+GMT%2B0000+(Greenwich+Mean+Time)&version=6.17.0&hosts=&consentId=c1e358a7-73d3-4686-aa3a-e88763d7b265&interactionCount=0&landingPath=NotLandingPage&groups=C0001%3A1%2CC0002%3A0%2CC0003%3A0&AwaitingReconsent=false");
            out.println("DNT: 1");
            out.println("Host: www.bbc.co.uk");
            out.println("sec-ch-ua: \" Not A;Brand\";v=\"99\", \"Chromium\";v=\"96\", \"Google Chrome\";v=\"96\"");
            out.println("sec-ch-ua-mobile: ?0");
            out.println("sec-ch-ua-platform: \"Linux\"");
            out.println("Sec-Fetch-Dest: document");
            out.println("Sec-Fetch-Mode: navigate");
            out.println("Sec-Fetch-Site: none");
            out.println("Sec-Fetch-User: ?1");
            out.println("Upgrade-Insecure-Requests: 1");
            out.println("User-Agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.110 Safari/537.36");
            out.println();
            out.flush();

            /*
             * Make sure there were no surprises
             */
            if (out.checkError())
                System.out.println(
                        "SSLSocketClient: java.io.PrintWriter error");

            /* read response */
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()));

            String inputLine;

            while ((inputLine = in.readLine()) != null)
                System.out.println(inputLine);

            in.close();
            out.close();
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
