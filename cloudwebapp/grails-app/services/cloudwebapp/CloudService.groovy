package cloudwebapp

import asset.pipeline.grails.AssetResourceLocator
import cloudservice.User
import cloudservice.commands.RegisterUserCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import cloudservice.interfaceobjects.RestfulResponse
import com.proxy.cloudListener.CloudListener
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import org.springframework.core.io.Resource
import org.springframework.messaging.simp.SimpMessagingTemplate

import javax.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets

class Temperature {
    Temperature(String temp, boolean isAdmin) {
        this.temp = temp
        this.isAdmin = isAdmin
    }
    String temp
    boolean isAdmin
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
    boolean nvrConnected = false
    int usersConnected = 0

    Account(String productId, String userName) {
        this.productId = productId
        this.userName = userName
    }
}

@Transactional
class CloudService {
    LogService logService
    CloudListener cloudListener = null
    UserService userService
    UserRoleService userRoleService
    RoleService roleService
    SpringSecurityService springSecurityService
    AssetResourceLocator assetResourceLocator
    SimpMessagingTemplate brokerMessagingTemplate;

    def start() {
        if (cloudListener == null)
            cloudListener = new CloudListener()

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
     * getVersion: Get the version from the config file application.yml. This version is generated by
     *             by git describe --tags
     * @return: The version string
     */
    def getVersion() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            Resource verRes = assetResourceLocator.findResourceForURI('./version.txt')
            String verStr = new String(verRes?.getInputStream()?.bytes, StandardCharsets.UTF_8)
            Version ver = new Version(verStr)
            result.responseObject = ver
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
            String[] auths = springSecurityService.getPrincipal().getAuthorities()
            if (auths.contains("ROLE_ADMIN")) {
                // No NVR to call for temperature when admin
                result.responseObject = new Temperature("temp=-10.0'C\n", true)
                return result
            }

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
            Temperature temp = new Temperature(out.toString(), false)
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
            String nvrSessionId = cloudListener.authenticate(cmd.productId)

            if (User.findByProductid(cmd.productId) != null)
                throw new Exception("Product ID " + cmd.productId + " is already registered")
            else if (User.findByUsername(cmd.username) != null)
                throw new Exception("Username " + cmd.username + " is already registered")

            if (nvrSessionId != "" && nvrSessionId != "NO_CONN") {
                User u = new User(username: cmd.username, productid: cmd.productId, password: cmd.password, email: cmd.email)
                u = userService.save(u)
                userRoleService.save(u, roleService.findByAuthority('ROLE_CLIENT'))
            } else if (nvrSessionId == "")
                throw new Exception("Failed to login to NVR")
            else
                throw new Exception("NVR not connected or entered product id was incorrect")
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " in CloudService.register: " + ex.getMessage())
            response.error = ex.getMessage()
            response.status = PassFail.FAIL
        }
        return response
    }

    ObjectCommandResponse getAccounts() {
        ObjectCommandResponse response = new ObjectCommandResponse()

        try {
            List<Account> accounts = new ArrayList<Account>()

            if (cloudListener != null) {
                List<User> users = User.getAll()
                Map<String, Integer> sessions = cloudListener.getSessions()

                users.forEach((User user) -> {
                    Account acc = new Account(user.getProductid(), user.getUsername())
                    accounts.add(acc)
                    if (sessions.containsKey(acc.productId)) {
                        acc.nvrConnected = true
                        if (sessions.get(acc.productId) > 0)
                            acc.usersConnected=sessions.get(acc.productId)
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

    def pushAccounts()
    {
        ObjectCommandResponse response = getAccounts()

        if(response.status != PassFail.FAIL) {
            List<Account> accounts = response.responseObject
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", accounts)
        }

    }
}
