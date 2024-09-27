package cloudwebapp

import cloudservice.commands.AddOrUpdateActiveMQCredsCmd
import cloudservice.commands.AdminChangeEmailCommand
import cloudservice.commands.AdminChangePasswordCommand
import cloudservice.commands.ChangeEmailCommand
import cloudservice.commands.DeleteAccountCommand
import cloudservice.commands.RegisterUserCommand
import cloudservice.commands.ChangePasswordCommand
import cloudservice.commands.ResetPasswordCommand
import cloudservice.commands.SendResetPasswordLinkCommand
import cloudservice.commands.SetAccountEnabledStatusCommand
import cloudservice.commands.SetupSMTPAccountCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.validation.ValidationErrors

class CloudController {
    CloudService cloudService
    UtilsService utilsService
    LogService logService
    ValidationErrorService validationErrorService
    UserAdminService userAdminService

    /**
     * getTemperature: Get the core temperature (Raspberry pi only). This is called at intervals to keep the session alive
     * @return: The temperature as a string. On non Raspberry pi systems an error is returned.
     */
    @Secured(['ROLE_CLIENT'])
    def getTemperature() {
        ObjectCommandResponse response = cloudService.getTemperature(request)

        if (response.status != PassFail.PASS)
            render([status: 500, text: response.error] as Map)
        else
            render response.responseObject as JSON
    }

    /**
     * register: Register a user account
     * @param cmd : Command object (username, NVR ProductID, password, confirmPassword, email, confirm email
     * @return
     */
    def register(RegisterUserCommand cmd) {
        response.contentType = "application/json"

        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'register')
            logService.cloud.error "register: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            ObjectCommandResponse registerResponse = cloudService.register(cmd)
            if (registerResponse.status != PassFail.PASS) {
                logService.cloud.error("Failed to register user: " + registerResponse.error)
                render([status: 500, text: registerResponse.error] as Map)
            } else {
                logService.cloud.info("User " + cmd.username + " registered successfully")
                render([status: 200, text: [message: "Registered " + cmd.username + " with product ID " + cmd.productId + " successfully"] as JSON] as Map)
            }
        }
    }

    @Secured(['ROLE_CLIENT', 'ROLE_ADMIN'])
    def changePassword(ChangePasswordCommand cmd) {
        ObjectCommandResponse result

        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
            logService.cloud.error "changePassword: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.changePassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "changePassword: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("changePassword: success")
                render ""
            }
        }
    }

    def resetPassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result

        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
            logService.cloud.error "resetPassword: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.resetPassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "resetPassword: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("resetPassword: success")
                render ""
            }
        }
    }
    @Secured(['ROLE_ADMIN'])
    def hasActiveMQCreds() {
        ObjectCommandResponse result = userAdminService.hasActiveMQCreds()

        if (result.status != PassFail.PASS) {
            render([status: 500, text: result.error] as Map)
        } else {
            logService.cloud.info("hasActiveMQCreds: (= ${result.responseObject}) success")
            render([text: result.responseObject] as Map)
        }
    }

    @Secured(['ROLE_ADMIN'])
    def addOrUpdateActiveMQCreds(AddOrUpdateActiveMQCredsCmd cmd) {
        ObjectCommandResponse result
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'addOrUpdateActiveMQCreds')
            logService.cloud.error "addOrUpdateActiveMQCreds: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.addOrUpdateActiveMQCreds(cmd)
            if (result.status != PassFail.PASS) {
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("addOrUpdateActiveMQCreds: success")
                render "success"
            }
        }
    }


    @Secured(['ROLE_ADMIN'])
    def getAccounts() {
        ObjectCommandResponse response = cloudService.getAccounts()
        if (response.status != PassFail.PASS)
            render([status: 500, text: response.error] as Map)
        else
            render response.responseObject as JSON
    }

    @Secured(['ROLE_ADMIN'])
    def getVersion() {
        ObjectCommandResponse response = cloudService.getVersion()
        if (response.status != PassFail.PASS)
            render([status: 500, text: response.error] as Map)
        else
            render response.responseObject as JSON
    }

    @Secured(['ROLE_ADMIN'])
    def setAccountEnabledStatus(SetAccountEnabledStatusCommand cmd) {
        ObjectCommandResponse response
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
            logService.cloud.error "setAccountEnabledStatus: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            response = userAdminService.setAccountEnabledStatus(cmd)
            if (response.status != PassFail.PASS) {
                render([status: 500, text: response.error] as Map)
            } else {
                logService.cloud.info("setAccountEnabledStatus: success")
                render ""
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    def setupSMTPClientLocally(SetupSMTPAccountCommand cmd) {
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'setupSMTPClientLocally')
            logService.cloud.error "setupSMTPClientLocally: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            ObjectCommandResponse response = utilsService.setupSMTPClient(cmd)
            if (response.status != PassFail.PASS)
                render([status: 500, text: response.error] as Map)
            else
                render ""
        }
    }

    @Secured(['ROLE_ADMIN'])
    def getSMTPClientParamsLocally() {
        ObjectCommandResponse response = utilsService.getSMTPClientParams()
        if (response.status != PassFail.PASS)
            render([status: 500, text: response.error] as Map)
        else if (response.response != null)
            render([status: 400, text: response.response] as Map)  // Warning, no config file present
        else
            render([status: 200, text: response.responseObject as JSON] as Map)
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
    def getEmail() {
        ObjectCommandResponse result = userAdminService.getEmail()
        if (result.status != PassFail.PASS) {
            render([status: 500, text: result.error] as Map)
        } else {
            logService.cloud.info("getEmail: success")
            render([text: result.responseObject as JSON] as Map)
        }
    }

    @Secured(['ROLE_ADMIN', 'ROLE_CLIENT'])
    def changeEmail(ChangeEmailCommand cmd) {
        ObjectCommandResponse result

        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changeEmail')
            logService.cloud.error "changeEmail: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.changeEmail(cmd)
            if (result.status != PassFail.PASS) {
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("changeEmail: success")
                render ""
            }
        }
    }


    @Secured(['ROLE_ADMIN'])
    def adminChangePassword(AdminChangePasswordCommand cmd) {
        ObjectCommandResponse result
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminChangePassword')
            logService.cloud.error "adminChangePassword: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.adminChangePassword(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminChangePassword: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("adminChangePassword: success")
                render ""
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    def adminChangeEmail(AdminChangeEmailCommand cmd) {
        ObjectCommandResponse result
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminChangeEmail')
            logService.cloud.error "adminChangeEmail: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.adminChangeEmail(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminChangeEmail: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("adminChangeEmail: success")
                render ""
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    def adminDeleteAccount(DeleteAccountCommand cmd) {
        ObjectCommandResponse result
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'adminDeleteAccount')
            logService.cloud.error "adminDeleteAccount: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.adminDeleteAccount(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "adminDeleteAccount: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("adminDeleteAccount: success")
                render ""
            }
        }
    }

    def sendResetPasswordLink(SendResetPasswordLinkCommand cmd) {
        ObjectCommandResponse result
        if (cmd.hasErrors()) {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'sendResetPasswordLink')
            logService.cloud.error "sendResetPasswordLink: Validation error: " + errorsMap.toString()
            render([status: 400, text: errorsMap as JSON] as Map)
        } else {
            result = userAdminService.sendResetPasswordLink(cmd)
            if (result.status != PassFail.PASS) {
                logService.cloud.error "sendResetPasswordLink: error: ${result.error}"
                render([status: 500, text: result.error] as Map)
            } else {
                logService.cloud.info("sendResetPasswordLink: success")
                render ""
            }
        }
    }

    def getUserAuthorities() {
        ObjectCommandResponse result
        result = userAdminService.getUserAuthorities()
        if (result.status != PassFail.PASS) {
            logService.cloud.error "getUserAuthorities: error: ${result.error}"
            render([status: 500, text: result.error] as Map)
        } else {
            logService.cloud.info("getUserAuthorities: success")
            render result.responseObject as JSON
        }
    }

    def isTransportActive() {
        ObjectCommandResponse resp = cloudService.isTransportActive()
        if (resp.status == PassFail.PASS)
            render([status: 200, text: [transportActive: resp.responseObject] as JSON] as Map)
        else
            render([status: 500, text: resp.error] as Map)
    }
}
