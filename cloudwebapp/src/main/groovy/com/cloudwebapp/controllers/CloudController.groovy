package com.cloudwebapp.controllers

import com.cloudwebapp.commands.AddOrUpdateActiveMQCredsCmd
import com.cloudwebapp.commands.AdminChangeEmailCommand
import com.cloudwebapp.commands.AdminChangePasswordCommand
import com.cloudwebapp.commands.ChangeEmailCommand
import com.cloudwebapp.commands.ChangeInstanceCountCommand
import com.cloudwebapp.commands.ChangePasswordCommand
import com.cloudwebapp.commands.DeleteAccountCommand
import com.cloudwebapp.commands.RegisterUserCommand
import com.cloudwebapp.commands.ResetPasswordCommand
import com.cloudwebapp.commands.SendResetPasswordLinkCommand
import com.cloudwebapp.commands.SetAccountEnabledStatusCommand
import com.cloudwebapp.commands.SetupSMTPAccountCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.enums.PassFail
import com.cloudwebapp.error.CloudRestMethodException
import com.cloudwebapp.interfaceobjects.ObjectCommandResponse
import com.cloudwebapp.security.TwoFactorAuthProvider
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.cloudwebapp.services.UserAdminService
import com.cloudwebapp.services.UtilsService
import com.cloudwebapp.services.ValidationErrorService
import com.cloudwebapp.validators.AddOrUpdateActiveMQCredsCmdValidator
import com.cloudwebapp.validators.AdminChangeEmailCommandValidator
import com.cloudwebapp.validators.AdminChangePasswordCommandValidator
import com.cloudwebapp.validators.ChangeEmailCommandValidator
import com.cloudwebapp.validators.ChangePasswordCommandValidator
import com.cloudwebapp.validators.DeleteAccountCommandValidator
import com.cloudwebapp.validators.GeneralValidator
import com.cloudwebapp.validators.RegisterUserCommandValidator
import com.cloudwebapp.validators.ResetPasswordCommandValidator
import com.cloudwebapp.validators.SendResetPasswordLinkCommandValidator
import com.cloudwebapp.validators.SetAccountEnabledStatusCommandValidator
import com.cloudwebapp.validators.SetupSMTPAccountCommandValidator
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.annotation.Secured
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/cloud")
class CloudController {
    @Autowired
    CloudService cloudService
    @Autowired
    UtilsService utilsService
    @Autowired
    LogService logService
    @Autowired
    ValidationErrorService validationErrorService
    @Autowired
    UserAdminService userAdminService
    @Autowired
    UserRepository userRepository
    @Autowired
    TwoFactorAuthProvider authenticationManager

    /**
     * getTemperature: Get the core temperature (Raspberry pi only). This is called at intervals to keep the session alive
     * @return: The temperature as a string. On non Raspberry pi systems an error is returned.
     */
    @Secured(['ROLE_CLIENT'])
    @RequestMapping("/getTemperature")
    def getTemperature(HttpServletRequest request) {
        ObjectCommandResponse response = cloudService.getTemperature(request)

        if (response.status != PassFail.PASS)
            throw new CloudRestMethodException(response.error, "cloud/getTemperature")
        else
            return response.responseObject
    }

    /**
     * register: Register a user account
     * @param cmd : Command object (username, NVR ProductID, password, confirmPassword, email, confirm email
     * @return
     */
    @RequestMapping("register")
    def register(@RequestBody RegisterUserCommand cmd) {
        def gv = new GeneralValidator(cmd, new RegisterUserCommandValidator(userRepository))
        def result = gv.validate()

        if (result.hasErrors()) {
            return gv.validationErrors("register", logService)
        } else {
            ObjectCommandResponse registerResponse = cloudService.register(cmd)
            if (registerResponse.status != PassFail.PASS) {
                logService.cloud.error("Failed to register user: " + registerResponse.error)
                throw new CloudRestMethodException(registerResponse.error, "cloud/register")
            } else {
                logService.cloud.info("User " + cmd.username + " registered successfully")
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body([message: "Registered " + cmd.username + " with product ID " + cmd.productId + " successfully"])
            }
        }
    }

    @Secured(['ROLE_CLIENT', 'ROLE_ADMIN'])
    @RequestMapping("/changePassword")
    def changePassword(@RequestBody ChangePasswordCommand cmd) {
        def gv = new GeneralValidator(cmd, new ChangePasswordCommandValidator(authenticationManager as AuthenticationManager))
        def bindingResult = gv.validate()
        ObjectCommandResponse result

        if (bindingResult.hasErrors()) {
            return gv.validationErrors("changePassword", logService)
        } else {
            result = userAdminService.changePassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "changePassword: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "cloud/changePassword")
            } else {
                logService.cloud.info("changePassword: success")
                return ResponseEntity.ok().body("")
            }
        }
    }

    @RequestMapping("/resetPassword")
    def resetPassword(@RequestBody ResetPasswordCommand cmd) {
        def gv = new GeneralValidator(cmd, new ResetPasswordCommandValidator())
        def bindingResult = gv.validate()
        ObjectCommandResponse result

        if (bindingResult.hasErrors()) {
            return gv.validationErrors("resetPassword", logService)
        } else {
            result = userAdminService.resetPassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "resetPassword: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "/cloud/resetPassword")
            } else {
                logService.cloud.info("resetPassword: success")
                return ResponseEntity.ok().body("")
            }
        }
    }
    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/hasActiveMQCreds")
    def hasActiveMQCreds() {
        ObjectCommandResponse result = userAdminService.hasActiveMQCreds()

        if (result.status != PassFail.PASS) {
            throw new CloudRestMethodException(result.error, "/cloud/hasActiveMQCreds")
        } else {
            logService.cloud.info("hasActiveMQCreds: (= ${result.responseObject}) success")
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.responseObject)
        }
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/addOrUpdateActiveMQCreds")
    def addOrUpdateActiveMQCreds(@RequestBody AddOrUpdateActiveMQCredsCmd cmd) {
        def gv = new GeneralValidator(cmd, new AddOrUpdateActiveMQCredsCmdValidator())
        def bindingResult = gv.validate()
        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("/cloud/addOrUpdateActiveMQCreds", logService)
        } else {
            result = userAdminService.addOrUpdateActiveMQCreds(cmd)
            if (result.status != PassFail.PASS) {
                throw new CloudRestMethodException(result.error, "/cloud/addOrUpdateActiveMQCreds")
            } else {
                logService.cloud.info("addOrUpdateActiveMQCreds: success")
                return ResponseEntity.ok().body(["success"])
            }
        }
    }


    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/getAccounts")
    def getAccounts() {
        ObjectCommandResponse response = cloudService.getAccounts()
        if (response.status != PassFail.PASS)
            throw new CloudRestMethodException(response.error, "/cloud/getAccounts")
        else
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.responseObject)
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/getVersion")
    def getVersion() {
        ObjectCommandResponse response = cloudService.getVersion()
        if (response.status != PassFail.PASS)
            throw new CloudRestMethodException(response.error, "/cloud/getVersion")
        else
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.responseObject)
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/setAccountEnabledStatus")
    def setAccountEnabledStatus(@RequestBody SetAccountEnabledStatusCommand cmd) {
        def gv = new GeneralValidator(cmd, new SetAccountEnabledStatusCommandValidator(userRepository))
        def bindingResult = gv.validate()
        ObjectCommandResponse response
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("setAccountEnabledStatus", logService)
        } else {
            response = userAdminService.setAccountEnabledStatus(cmd)
            if (response.status != PassFail.PASS) {
                throw new CloudRestMethodException(response.error, "/cloud/setAccountEnabledStatus")
            } else {
                logService.cloud.info("setAccountEnabledStatus: success")
                return ResponseEntity.ok().body("")
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/setupSMTPClientLocally")
    def setupSMTPClientLocally(@RequestBody SetupSMTPAccountCommand cmd) {
        def gv = new GeneralValidator(cmd, new SetupSMTPAccountCommandValidator())
        def bindingResult  = gv.validate()
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("/setupSMTPClientLocally", logService)
        } else {
            ObjectCommandResponse response = utilsService.setupSMTPClient(cmd)
            if (response.status != PassFail.PASS)
                throw new CloudRestMethodException(response.error, "/cloud/setupSMTPClientLocally/")
            else
                return ResponseEntity.ok().body([""])
        }
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/getSMTPClientParamsLocally")
    def getSMTPClientParamsLocally() {
        ObjectCommandResponse response = utilsService.getSMTPClientParams()
        if (response.status != PassFail.PASS)
            throw new CloudRestMethodException(response.error, "/getSMTPClientParamsLocally")
        else if (response.response != null)
            return ResponseEntity.badRequest().body(response.response)  // Warning, no config file present
        else
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response.responseObject)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
    @RequestMapping("/getEmail")
    def getEmail() {
        ObjectCommandResponse result = userAdminService.getEmail()
        if (result.status != PassFail.PASS) {
            throw new CloudRestMethodException(result.error, "/cloud/getEmail")
        } else {
            logService.cloud.info("getEmail: success")
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.responseObject)
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
    @RequestMapping("/changeEmail")
    def changeEmail(@RequestBody ChangeEmailCommand cmd) {
        def gv = new GeneralValidator(cmd, new ChangeEmailCommandValidator(authenticationManager as AuthenticationManager, userRepository))
        def bindingResult = gv.validate()

        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("/cloud/changeEmail", logService)
        } else {
            result = userAdminService.changeEmail(cmd)
            if (result.status != PassFail.PASS) {
                throw new CloudRestMethodException(result.error, "/cloud/changeEmail")
            } else {
                logService.cloud.info("changeEmail: success")
                return ResponseEntity.ok().body([""])
            }
        }
    }


    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/adminChangePassword")
    def adminChangePassword(@RequestBody AdminChangePasswordCommand cmd) {
        def gv = new GeneralValidator(cmd, new AdminChangePasswordCommandValidator(userRepository))
        def bindingResult = gv.validate()
        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("adminChangePassword", logService)
        } else {
            result = userAdminService.adminChangePassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminChangePassword: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "/cloud/adminChangePassword")
            } else {
                logService.cloud.info("adminChangePassword: success")
                return ResponseEntity.ok().body("")
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/adminChangeEmail")
    def adminChangeEmail(@RequestBody AdminChangeEmailCommand cmd) {
        def gv = new GeneralValidator(cmd, new AdminChangeEmailCommandValidator(userRepository))
        def bindingResult = gv.validate()
        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("adminChangeEmail", logService)
        } else {
            result = userAdminService.adminChangeEmail(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminChangeEmail: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "/cloud/adminChangeEmail")
            } else {
                logService.cloud.info("adminChangeEmail: success")
                return ResponseEntity.ok().body("")
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    @RequestMapping("/adminDeleteAccount")
    def adminDeleteAccount(@RequestBody DeleteAccountCommand cmd) {
        def gv = new GeneralValidator(cmd, new DeleteAccountCommandValidator(userRepository))
        BindingResult bindingResult = gv.validate()
        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("adminDeleteAccount", logService)
        } else {
            result = userAdminService.adminDeleteAccount(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminDeleteAccount: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "/cloud/adminDeleteAccount")
            } else {
                logService.cloud.info("adminDeleteAccount: success")
                return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body( "")
            }
        }
    }

    @RequestMapping("/sendResetPasswordLink")
    def sendResetPasswordLink(@RequestBody SendResetPasswordLinkCommand cmd) {
        def gv = new GeneralValidator(cmd, new SendResetPasswordLinkCommandValidator())
        def bindingResult = gv.validate()

        ObjectCommandResponse result
        if (bindingResult.hasErrors()) {
            return gv.validationErrors("sendResetPasswordLink", logService)
        } else {
            result = userAdminService.sendResetPasswordLink(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "sendResetPasswordLink: error: ${result.error}"
                throw new CloudRestMethodException(result.error, "/cloud/sendResetPasswordLink")
            } else {
                logService.cloud.info("sendResetPasswordLink: success")
                return ResponseEntity.ok().body("")
            }
        }
    }

    @RequestMapping("/getUserAuthorities")
    def getUserAuthorities() {
        ObjectCommandResponse result
        result = userAdminService.getUserAuthorities()
        if (result.status != PassFail.PASS) {
            logService.cloud.error "getUserAuthorities: error: ${result.error}"
            throw new CloudRestMethodException(result.error, "cloud/getUserAuthorities")
        } else {
            logService.cloud.info("getUserAuthorities: success")
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result.responseObject)
        }
    }

    @RequestMapping("/isTransportActive")
    def isTransportActive() {
        ObjectCommandResponse resp = cloudService.isTransportActive()
        if (resp.status == PassFail.PASS)
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body([transportActive: resp.responseObject] )
        else
            throw new CloudRestMethodException(resp.error, "/cloud/isTransportActive")
    }

    /**
     * changeInstanceCount: This is called on closing the browser when logged in as a client. It decrements the
     *                               session count (as shown on Accounts Admin page in admin mode), and  removes the
     *                               NVRSESSIONID from the browser. The call is made as an async call to ensure it
     *                               returns before the browser closes/
     * @param productId : Product ID (received as cookie)
     * @param nvrSessionId : NVRSESSIONID (received as cookie)
     * @param cmd : The count is incremented if increment is true, otherwise it is decremented.
     * @return
     */
    @RequestMapping("/changeInstanceCount")
    def changeInstanceCount(@CookieValue(value = "PRODUCTID", defaultValue = "empty") String productId,
                            @CookieValue(value = "NVRSESSIONID", defaultValue = "empty") String nvrSessionId,
                            @RequestBody ChangeInstanceCountCommand cmd) {
        try {
            if (productId != "empty" && nvrSessionId != "empty") {
                def cloudMq = cloudService.cloudListener.instances.get(productId)
                if (cmd.increment)
                    cloudMq.incSessionCount(nvrSessionId)
                else
                    cloudMq.decSessionCount(nvrSessionId)
            } else {
                logService.cloud.error("Error in changeInstanceCount. no value for cookie (PRODUCTID = ${productId}, NVRSESSIONID = ${nvrSessionId})")
            }
        }
        catch (Exception ex) {
            logService.cloud.error("${ex.getClass().getName()} in changeInstanceCount. ${ex.getMessage()}")
        }

        // httpResp.setHeader("Set-Cookie", "NVRSESSIONID=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;")
    }

    /**
     * rme.js: This is referenced from the index.html file and is only used to trigger the RememeberMeServices autoLogin
     *         into returning the NVRSESSIONID and PRODUCTID (if rememberme refers to a client account). This ensures
     *         that the Angular code can call getUserAuthorities in a timely manner to set up the nav bar
     *         mode correctly.
     * @return
     */
    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
    @RequestMapping("/rme.js")
    def rememberMeEnabler() {
        return ResponseEntity.ok().body("let x = 6;")
    }
}
