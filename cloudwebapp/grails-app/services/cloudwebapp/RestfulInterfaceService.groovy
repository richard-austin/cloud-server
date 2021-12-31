package cloudwebapp

import cloudservice.entityobjects.RestfulResponseStatusEnum
import cloudservice.interfaceobjects.RestfulResponse
import com.sun.net.httpserver.HttpServer
import grails.gorm.transactions.Transactional

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Transactional
class RestfulInterfaceService {
    LogService logService
    boolean initialised = false
    String NVRSESSIONID
    String baseUrl
    private final int threadPoolSize = 20

    private final ExecutorService inputReadExecutor = Executors.newFixedThreadPool(threadPoolSize);

    void initialise() {
        if (!initialised) {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = [new X509TrustManager() {
                @Override
                void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                }

                @Override
                X509Certificate[] getAcceptedIssuers() {
                    return null
                }
            }
            ]

            KeyStore keyStore = KeyStore.getInstance("JKS")
            keyStore.load(new FileInputStream(RestfulProperties.KEYSTORE_PATH), RestfulProperties.KEYSTORE_PASSWORD.toCharArray())

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SUNX509")
            kmf.init(keyStore, RestfulProperties.KEY_PASSWORD.toCharArray())
            KeyManager[] keyManagers = kmf.getKeyManagers()
            SSLContext sc = SSLContext.getInstance("TLS")

            sc.init(keyManagers, trustAllCerts, new SecureRandom())

            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
            initialised = true
            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                boolean verify(String hostname, SSLSession session) {
                    return true
                }
            }

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        }
    }

    class RestfulProperties {
        static final String USERNAME = "admin"
        static final String PASSWORD = "P0larC@mper"
        static final int REQUEST_TIMEOUT_SECS = 300
        static final String KEYSTORE_PATH = "/home/richard/cloud.jks"
        static final String KEYSTORE_PASSWORD = "@lbuquerq"
        static final String KEY_PASSWORD = "@lbuquerq"
    }

    /**
     * Attempt to send a RESTFul request to a given host to perform a service
     *
     * @param address : network address of the target host
     * @param controller : controller that will process the API call at the host
     * @param service : service required at the host
     * @param params : map of any key:value parameters needed in the API call, null if there are none
     * @return : RestfulResponse object containing details of the result of the request, including an object containing the data received back from the host if any
     */
    OutputStream sendRequest(HttpServletRequest req, HttpServletResponse res) {
        HttpsURLConnection conn = null
        InputStream is = null
        Reader inp = null
        String url = baseUrl + req.requestURI
        url = url.replaceFirst(/\/link\/proxy\//, "/")
        try {
            URL u = new URL(url)
            conn = (HttpsURLConnection) u.openConnection(Proxy.NO_PROXY)
            def x = req.getMethod()
            logService.cloud.debug("Method is ${x}")
            conn.setRequestMethod(x)
            conn.setDoOutput(true)
//            conn.setRequestProperty("Content-Type", "application/json; utf-8")
//            conn.setRequestProperty("Accept", "application/json")
//            conn.setRequestProperty("Accept-Charset", "UTF-8")
            def headerNames = req.getHeaderNames()
            for (String headername : headerNames)
                conn.setRequestProperty(headername, req.getHeader(headername))

            conn.setRequestProperty("Cookie", NVRSESSIONID)

            conn.setConnectTimeout(RestfulProperties.REQUEST_TIMEOUT_SECS * 1000)
            conn.setReadTimeout(RestfulProperties.REQUEST_TIMEOUT_SECS * 1000)
            conn.setInstanceFollowRedirects(false)

            final InputStream apiIn = req.getInputStream()
            final OutputStream cloudOut = conn.getOutputStream()
             inputReadExecutor.submit(() -> {
                def bis = new BufferedInputStream(apiIn)
                final char[] buf = new char[0x10000]
                StringBuilder out = new StringBuilder()
                inp = new InputStreamReader(bis, "UTF-8")
                int readCount
                while((readCount = inp.read(buf, 0, buf.length)) != -1)
                {
                    out.append(buf, 0, readCount)
                    logService.cloud.info(log(toBytes(buf, readCount), readCount))
                    cloudOut.write(toBytes(buf, out.length()))
                    cloudOut.flush()
                }
            })

            def responseCode = conn.getResponseCurlconnectionode()

            if (responseCode == HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream()
            } else {
                is = conn.getErrorStream()
            }
            def os = res.getOutputStream()

            // get the response or error message from the relevant stream and store as a string
            def bis = new BufferedInputStream(is)
            final char[] buffer = new char[0x10000]
            StringBuilder out = new StringBuilder()
            inp = new InputStreamReader(bis, "UTF-8")
            int readCount
            while ((readCount = inp.read(buffer, 0, buffer.length)) != -1) {
                if (readCount > 0) {
                    out.append(buffer, 0, readCount)
                    os.write(toBytes(buffer, readCount))
                    os.flush()
                }
            }
            os.close()
            res.setStatus(responseCode)
        }
        catch (IOException ex) {
            logService.cloud.error(ex.getMessage())
        }
        catch (CertificateException e) {
            logService.cloud.error(e.getMessage())
        }
        catch (Exception e) {
            logService.cloud.error(e.getMessage())
        }
        finally {
            if (conn && conn.getErrorStream()) {
                conn.getErrorStream().close()
            }
            if (is) {
                is.close()
            }
            if (inp) {
                inp.close()
            }
            conn.disconnect()
        }
    }

    byte[] toBytes(char[] chars, int length) {
        CharBuffer charBuffer = CharBuffer.wrap(chars, 0, length)
        ByteBuffer byteBuffer = Charset.forName("UTF-8").encode(charBuffer)
        byte[] bytes = Arrays.copyOfRange(byteBuffer.array(),
                byteBuffer.position(), byteBuffer.limit())
        Arrays.fill(byteBuffer.array(), (byte) 0) // clear sensitive data
        return bytes
    }

    def authenticate(String strUrl) {
        baseUrl = strUrl
        def result = new RestfulResponse()
        result.status = RestfulResponseStatusEnum.FAIL
        result.responseObject = null
        result.responseCode = -1
        result.errorMsg = null

        initialise()

        HttpsURLConnection conn = null
        InputStream is = null
        Reader inp = null
        URL u = new URL(strUrl + "/login/authenticate")
        conn = (HttpsURLConnection) u.openConnection(Proxy.NO_PROXY)
        conn.setRequestMethod("POST")
        conn.setDoOutput(true)
        conn.setConnectTimeout(RestfulProperties.REQUEST_TIMEOUT_SECS * 1000)
        conn.setReadTimeout(RestfulProperties.REQUEST_TIMEOUT_SECS * 1000)
        conn.setInstanceFollowRedirects(false)
        OutputStream os = conn.getOutputStream()
        byte[] byteParams = ("username=" + RestfulProperties.USERNAME + "&password=" + RestfulProperties.PASSWORD).getBytes()
        os.write(byteParams)
        result.responseCode = conn.getResponseCode()

        if (result.responseCode == HttpURLConnection.HTTP_MOVED_TEMP ) {
            {
                String tmp = conn.getHeaderField("Set-Cookie")
                result.responseObject = NVRSESSIONID = tmp.substring(0, 43)
            }
        } else {
            is = conn.getErrorStream()
            // get the response or error message from the relevant stream and store as a string
            def bis = new BufferedInputStream(is)
            final char[] buffer = new char[0x10000]
            StringBuilder out = new StringBuilder()
            inp = new InputStreamReader(bis, "UTF-8")
            int readCount = 1
            while (readCount > 0) {
                readCount = inp.read(buffer, 0, buffer.length)
                if (readCount > 0) {
                    out.append(buffer, 0, readCount)
                }
            }
            result.responseObject = out.toString()
        }
        return NVRSESSIONID
    }

    /**
     * Build the URL to call the required service at the given host, including parameters if any.
     *
     * @param address : network address of the host (e.g. IP address, FQDN)
     * @param controller : controller that will process the API call
     * @param service : service required at the host
     * @return : the constructed URL
     */
    private static String buildURL(String address, String controller, String service) {
        StringBuilder url = new StringBuilder()
        url.append('https://')
        url.append(address)
//        url.append('/cloud')
        url.append('/')
        url.append(controller)
        url.append('/')
        url.append(service)

        return url.toString()
    }

    /**
     * buildJSON: Build a JSON string of the input parameters given in the map
     * @param params Parameters as key/value pairs
     */
    private static String buildJSON(Map<String, String> params) {
        StringBuilder json = new StringBuilder()
        json.append("{")

        if (params && params.size() > 0) {

            boolean setComma = false
            params.each { it ->
                if (it.value != null) {
                    if (setComma)
                        json.append(", ")
                    else
                        setComma = true

                    json.append("\"$it.key\": \"$it.value\"")
                }
            }
        }
        json.append("}")
        return json.toString()
    }

    private String log(byte[] dataBytes, int length) {
        return new String(Arrays.copyOfRange(dataBytes, 0, length))
    }

}
