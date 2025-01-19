package com.cloudwebapp.controllers

import com.cloudwebapp.commands.AddOrUpdateActiveMQCredsCmd
import com.cloudwebapp.commands.AdminChangeEmailCommand
import com.cloudwebapp.commands.AdminChangePasswordCommand
import com.cloudwebapp.commands.ChangeEmailCommand
import com.cloudwebapp.commands.ChangePasswordCommand
import com.cloudwebapp.commands.DeleteAccountCommand
import com.cloudwebapp.commands.RegisterUserCommand
import com.cloudwebapp.commands.ResetPasswordCommand
import com.cloudwebapp.commands.SendResetPasswordLinkCommand
import com.cloudwebapp.commands.SetAccountEnabledStatusCommand
import com.cloudwebapp.commands.SetupSMTPAccountCommand
import com.cloudwebapp.enums.PassFail
import com.cloudwebapp.error.CloudRestMethodException
import com.cloudwebapp.interfaceobjects.ObjectCommandResponse
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.cloudwebapp.services.UserAdminService
import com.cloudwebapp.services.UtilsService
import com.cloudwebapp.services.ValidationErrorService
import com.cloudwebapp.validators.BadRequestResult
import com.cloudwebapp.validators.GeneralValidator
import com.cloudwebapp.validators.RegisterUserCommandValidator
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.bind.validation.ValidationErrors
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.annotation.Secured
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
        def gv = new GeneralValidator(cmd, new RegisterUserCommandValidator())
        def result = gv.validate()

        if (result.hasErrors()) {
            logService.cloud.error "register: Validation error: " + result.toString()
            BadRequestResult retVal = new BadRequestResult(result)
            return ResponseEntity
                    .badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(retVal)
        } else {
            ObjectCommandResponse registerResponse = cloudService.register(cmd)
            if (registerResponse.status != PassFail.PASS) {
                logService.cloud.error("Failed to register user: " + registerResponse.error)
                throw new CloudRestMethodException(registerResponse.error, "cloud/register")
            } else {
                logService.cloud.info("User " + cmd.username + " registered successfully")
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(MediaType.APPLICATION_JSON).ok("Registered " + cmd.username + " with product ID " + cmd.productId + " successfully")
            }
        }
    }

//    @Secured(['ROLE_CLIENT', 'ROLE_ADMIN'])
//    def changePassword(ChangePasswordCommand cmd) {
//        ObjectCommandResponse result
//
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
//            logService.cloud.error "changePassword: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.changePassword(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "changePassword: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("changePassword: success")
//                render ""
//            }
//        }
//    }
//
//    def resetPassword(ResetPasswordCommand cmd) {
//        ObjectCommandResponse result
//
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
//            logService.cloud.error "resetPassword: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.resetPassword(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "resetPassword: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("resetPassword: success")
//                render ""
//            }
//        }
//    }
//    @Secured(['ROLE_ADMIN'])
//    def hasActiveMQCreds() {
//        ObjectCommandResponse result = userAdminService.hasActiveMQCreds()
//
//        if (result.status != PassFail.PASS) {
//            render([status: 500, text: result.error] as Map)
//        } else {
//            logService.cloud.info("hasActiveMQCreds: (= ${result.responseObject}) success")
//            render([text: result.responseObject] as Map)
//        }
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def addOrUpdateActiveMQCreds(AddOrUpdateActiveMQCredsCmd cmd) {
//        ObjectCommandResponse result
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'addOrUpdateActiveMQCreds')
//            logService.cloud.error "addOrUpdateActiveMQCreds: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.addOrUpdateActiveMQCreds(cmd)
//            if (result.status != PassFail.PASS) {
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("addOrUpdateActiveMQCreds: success")
//                render "success"
//            }
//        }
//    }
//
//
//    @Secured(['ROLE_ADMIN'])
//    def getAccounts() {
//        ObjectCommandResponse response = cloudService.getAccounts()
//        if (response.status != PassFail.PASS)
//            render([status: 500, text: response.error] as Map)
//        else
//            render response.responseObject as JSON
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def getVersion() {
//        ObjectCommandResponse response = cloudService.getVersion()
//        if (response.status != PassFail.PASS)
//            render([status: 500, text: response.error] as Map)
//        else
//            render response.responseObject as JSON
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def setAccountEnabledStatus(SetAccountEnabledStatusCommand cmd) {
//        ObjectCommandResponse response
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
//            logService.cloud.error "setAccountEnabledStatus: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            response = userAdminService.setAccountEnabledStatus(cmd)
//            if (response.status != PassFail.PASS) {
//                render([status: 500, text: response.error] as Map)
//            } else {
//                logService.cloud.info("setAccountEnabledStatus: success")
//                render ""
//            }
//        }
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def setupSMTPClientLocally(SetupSMTPAccountCommand cmd) {
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'setupSMTPClientLocally')
//            logService.cloud.error "setupSMTPClientLocally: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            ObjectCommandResponse response = utilsService.setupSMTPClient(cmd)
//            if (response.status != PassFail.PASS)
//                render([status: 500, text: response.error] as Map)
//            else
//                render ""
//        }
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def getSMTPClientParamsLocally() {
//        ObjectCommandResponse response = utilsService.getSMTPClientParams()
//        if (response.status != PassFail.PASS)
//            render([status: 500, text: response.error] as Map)
//        else if (response.response != null)
//            render([status: 400, text: response.response] as Map)  // Warning, no config file present
//        else
//            render([status: 200, text: response.responseObject as JSON] as Map)
//    }
//
//    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
//    def getEmail() {
//        ObjectCommandResponse result = userAdminService.getEmail()
//        if (result.status != PassFail.PASS) {
//            render([status: 500, text: result.error] as Map)
//        } else {
//            logService.cloud.info("getEmail: success")
//            render([text: result.responseObject as JSON] as Map)
//        }
//    }
//
//    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
//    def changeEmail(ChangeEmailCommand cmd) {
//        ObjectCommandResponse result
//
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changeEmail')
//            logService.cloud.error "changeEmail: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.changeEmail(cmd)
//            if (result.status != PassFail.PASS) {
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("changeEmail: success")
//                render ""
//            }
//        }
//    }
//
//
//    @Secured(['ROLE_ADMIN'])
//    def adminChangePassword(AdminChangePasswordCommand cmd) {
//        ObjectCommandResponse result
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminChangePassword')
//            logService.cloud.error "adminChangePassword: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.adminChangePassword(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "adminChangePassword: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("adminChangePassword: success")
//                render ""
//            }
//        }
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def adminChangeEmail(AdminChangeEmailCommand cmd) {
//        ObjectCommandResponse result
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminChangeEmail')
//            logService.cloud.error "adminChangeEmail: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.adminChangeEmail(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "adminChangeEmail: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("adminChangeEmail: success")
//                render ""
//            }
//        }
//    }
//
//    @Secured(['ROLE_ADMIN'])
//    def adminDeleteAccount(DeleteAccountCommand cmd) {
//        ObjectCommandResponse result
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminDeleteAccount')
//            logService.cloud.error "adminDeleteAccount: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.adminDeleteAccount(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "adminDeleteAccount: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("adminDeleteAccount: success")
//                render ""
//            }
//        }
//    }
//
//    def sendResetPasswordLink(SendResetPasswordLinkCommand cmd) {
//        ObjectCommandResponse result
//        if (cmd.hasErrors()) {
//            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'sendResetPasswordLink')
//            logService.cloud.error "sendResetPasswordLink: Validation error: " + errorsMap.toString()
//            render([status: 400, text: errorsMap as JSON] as Map)
//        } else {
//            result = userAdminService.sendResetPasswordLink(cmd)
//            if (result.status != PassFail.PASS) {
//                logService.cloud.error "sendResetPasswordLink: error: ${result.error}"
//                render([status: 500, text: result.error] as Map)
//            } else {
//                logService.cloud.info("sendResetPasswordLink: success")
//                render ""
//            }
//        }
//    }
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
//
//    def isTransportActive() {
//        ObjectCommandResponse resp = cloudService.isTransportActive()
//        if (resp.status == PassFail.PASS)
//            render([status: 200, text: [transportActive: resp.responseObject] as JSON] as Map)
//        else
//            render([status: 500, text: resp.error] as Map)
//    }
}
