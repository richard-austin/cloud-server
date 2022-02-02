package cloudwebapp

import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import cloudservice.interfaceobjects.RestfulResponse
import com.proxy.CloudListener
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional

import javax.servlet.http.HttpServletRequest

class Temperature {
    Temperature(String temp) {
        this.temp = temp
    }
    String temp
}

@Transactional
class CloudService {
    LogService logService
    GrailsApplication grailsApplication
    CloudListener cloudListener = null

    def start() {
        if(cloudListener == null)
            cloudListener = new CloudListener()

        ObjectCommandResponse response = new ObjectCommandResponse()
        try
        {
            cloudListener.start()
        }
        catch(Exception ex)
        {
            response.status= PassFail.FAIL
            response.error = "Exception in CloudListener.start: "+ex.getClass().getName()+": "+ex.getMessage()
            logService.cloud.error(response.error)
        }
        return response
    }

    def stop() {
        if(cloudListener)
            cloudListener.stop()
    }

    def getTemperature(HttpServletRequest request) {
        RestfulResponse response = new RestfulResponse()
        ObjectCommandResponse result = new ObjectCommandResponse()
        InputStream is = null

        Reader inp = null
        try {
            URL url = new URL("http://localhost:8083/utils/getTemperature")

            HttpURLConnection conn = (HttpURLConnection) url.openConnection()
            conn.setRequestMethod("POST")
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Charset", "UTF-8")
            String cookie = request.getHeader("cookie")
            conn.setRequestProperty("Cookie", cookie)
            conn.setConnectTimeout(2000)
            conn.setReadTimeout(2000)
            conn.setDoOutput(true)

            response.responseCode = conn.getResponseCode()
            if (response.responseCode == HttpURLConnection.HTTP_OK) {
                is = conn.getInputStream()
            } else {
                is = conn.getErrorStream()
            }

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
            Temperature temp = new Temperature(out.toString())
            result.responseObject = temp
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in getTemperature: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

}
