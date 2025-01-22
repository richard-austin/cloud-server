package com.cloudwebapp.services

import com.cloudwebapp.commands.RegisterUserCommand
import com.cloudwebapp.dao.RoleRepository
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.enums.PassFail
import com.cloudwebapp.interfaceobjects.ObjectCommandResponse
import com.cloudwebapp.interfaceobjects.RestfulResponse
import com.cloudwebapp.model.User
import com.proxy.CloudMQ
import com.proxy.cloudListener.CloudMQListener
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.util.ResourceUtils

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

class Temperature {
    Temperature(String temp) {
        this.temp = temp
    }
    String temp
 }

class Version {
    Version(String version) {
        this.version = version
    }

    String version
}

class Account {
    String productId
    String userName
    String email
    boolean accountCreated = false
    boolean accountEnabled = false
    boolean nvrConnected = false
    int usersConnected = 0

    Account(String productId, String userName, String email) {
        this.productId = productId
        this.userName = userName
        this.email = email
    }
}

@Service
class CloudService {
    @Autowired
    LogService logService
    CloudMQListener cloudListener = null
    @Autowired
    UserRepository userRepository
    @Autowired
    RoleRepository roleRepository
    @Autowired
    SimpMessagingTemplate brokerMessagingTemplate
    @Autowired
    PasswordEncoder passwordEncoder

    final String update = new JSONObject()
            .put("message", "update")
            .toString()

    Map<String, String>_authenticatedNVRs = new ConcurrentHashMap<>()

    def start() {
        if (cloudListener == null)
            cloudListener = new CloudMQListener(brokerMessagingTemplate)

        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
            cloudListener.start()
        }
        catch (Exception ex) {
            response.status = PassFail.FAIL
            response.error = ex.getClass().getName() + " in CloudListener.start: " + ex.getClass().getName() + ": " + ex.getMessage()
            logService.cloud.error(response.error)
        }
        return response
    }

    def stop() {
        if (cloudListener)
            cloudListener.stop()
    }

    /**
     * getVersion: Get the version from the version.txt file. This version is generated by
     *             by git describe --tags
     * @return: The version string
     */
    def getVersion() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        result.responseObject = new Version("unknown")
        try {
            InputStream verRes = new ClassPathResource("version.txt").getInputStream()
            try (BufferedReader br = new BufferedReader(new InputStreamReader(verRes))) {
                String verStr = br.readLine()
                def ver = new Version(verStr)
                result.responseObject = ver
            }
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in getVersion: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    def getTemperature(HttpServletRequest request) {
        RestfulResponse response = new RestfulResponse()
        ObjectCommandResponse result = new ObjectCommandResponse()

        InputStream is

        Reader inp
        try {
            URL url = new URI("http://localhost:8083/utils/getTemperature").toURL()

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
            logService.cloud.error(ex.getClass().getName() + " in getTemperature: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    /**
     * register: Register a new user
     * @param cmd : Command object (username, NVR ProductID, password, confirmPassword, email, confirm email
     * @return
     */
    ObjectCommandResponse register(RegisterUserCommand cmd) {
        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
             if (userRepository.findByProductid(cmd.productId) != null)
                throw new Exception("Product ID " + cmd.productId + " is already registered")
            else if (userRepository.findByUsername(cmd.username) != null)
                throw new Exception("Username " + cmd.username + " is already registered")

            CloudMQ cloud = cloudListener.getInstances().get(cmd.productId.trim())
            if (cloud != null) {
                def roles = Collections.singletonList(roleRepository.findByName('ROLE_CLIENT'))
                User u = new User(username: cmd.username, productid: cmd.productId, password: passwordEncoder.encode(cmd.password), email: cmd.email, roles: roles, enabled: true, credentialsNonExpired: true)
                userRepository.save(u)
            } else
                throw new Exception("Cannot find an NVR for product ID ${cmd.productId}")

            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " in CloudService.register: " + ex.getMessage())
            response.error = ex.getMessage()
            response.status = PassFail.FAIL
        }
        return response
    }

    /**
     * getAccounts: Get all the registered accounts and online NVR's with no registered account
     * @return: Account data
     */
    ObjectCommandResponse getAccounts() {
        ObjectCommandResponse response = new ObjectCommandResponse()

        try {
            List<Account> accounts = new ArrayList<Account>()

            if (cloudListener != null) {
                List<User> users = userRepository.findAll()
                Map<String, CloudMQ> instances = cloudListener.getInstances()

                users.forEach((User user) -> {
                    if(user.getProductid() != "0000-0000-0000-0000") {   // Don't include the admin account
                        Account acc = new Account(user.getProductid(), user.getUsername(), user.getEmail())
                        acc.accountCreated = true
                        acc.accountEnabled = user.getEnabled()
                        accounts.add(acc)
                        if (instances.containsKey(acc.productId)) {
                            acc.nvrConnected = true
                            acc.usersConnected = instances.get(user.getProductid()).getSessionCount()
                        }
                    }
                })

                // Add any connected NVR's where no account has been created
                instances.forEach((key, cloud) -> {
                    if (!accounts.find((account) -> { account.getProductId() == key })) {
                        Account acc = new Account(key, '', '')
                        acc.accountCreated = false
                        acc.nvrConnected = true
                        accounts.add(acc)
                    }
                })
                response.responseObject = accounts
            }
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " exception in getAccounts: " + ex.getMessage())
            response.status = PassFail.FAIL
            response.error = ex.getMessage()
        }

        return response
    }

    /**
     * authenticatedNVRs: Map the newly acquired NVRSESSIONID against the product ID, to pass to the CloudSecurityEventListener
     *                    onAuthenticationSuccess method, which will set the NVR session ID up as a cookie so an NVR
     *                    session is established on the browser.
     * @param productId
     * @param nvrSessionId
     */
    void authenticatedNVRs(String productId, String nvrSessionId) {
        _authenticatedNVRs.put(productId, nvrSessionId)
    }

    /**
     * authenticatedNVRs: Returns the nvrSessionId for the given productID. Used by the CloudSecurityEventListener
     *                    onAuthenticationSuccess method, which will set the NVR session ID up as a cookie so an NVR
     *                    session is established on the browser
     * @param productId
     * @return
     */
    String authenticatedNVRs(String productId)
    {
        return _authenticatedNVRs.remove(productId)
    }

    ObjectCommandResponse isTransportActive() {
        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
            response.responseObject = cloudListener == null ? false : cloudListener.isTransportActive()
        }
        catch (Exception ex) {
            response.status = PassFail.FAIL
            response.error = "Exception in CloudService.isTransportActive: " + ex.getClass().getName() + ": " + ex.getMessage()
            logService.cloud.error(response.error)
        }
        return response
    }

}
