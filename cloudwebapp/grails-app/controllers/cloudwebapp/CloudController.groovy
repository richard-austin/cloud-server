package cloudwebapp

import cloudservice.commands.RegisterUserCommand
import cloudservice.commands.ResetPasswordCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.validation.ValidationErrors
import grails.web.controllers.ControllerMethod
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.security.access.prepost.PreAuthorize

import java.security.Principal

class CloudController {
    CloudService cloudService
    LogService logService
    ValidationErrorService validationErrorService
    UserAdminService userAdminService
    /**
     * getTemperature: Get the core temperature (Raspberry pi only). This is called at intervals to keep the session alive
     * @return: The temperature as a string. On non Raspberry pi systems an error is returned.
     */
    @Secured(['ROLE_CLIENT', 'ROLE_ADMIN'])
    def getTemperature() {
        ObjectCommandResponse response = cloudService.getTemperature(request)

        if (response.status != PassFail.PASS)
            render(status: 500, text: response.error)
        else
            render response.responseObject as JSON
    }

    /**
     * register: Register a user account
     * @param cmd: Command object (username, NVR ProductID, password, confirmPassword, email, confirm email
     * @return
     */
    def register(RegisterUserCommand cmd)
    {
        response.contentType = "application/json"

        if(cmd.hasErrors())
        {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'register')
            logService.cloud.error "register: Validation error: " + errorsMap.toString()
            render(status: 400, text: errorsMap as JSON)
        }
        else
        {
            ObjectCommandResponse registerResponse = cloudService.register(cmd)
            if(registerResponse.status != PassFail.PASS) {
                logService.cloud.error("Failed to register user: " + registerResponse.error)
                render(status: 500, text: registerResponse.error)
            }
            else {
                logService.cloud.info("User "+cmd.username+" registered successfully")
                render(status: 200, text: [message: "Registered "+cmd.username+" with product ID "+cmd.productId+" successfully"] as JSON)
            }
        }
    }

    @Secured(['ROLE_CLIENT', 'ROLE_ADMIN'])
    def changePassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result

        if(cmd.hasErrors())
        {
            def errorsMap = validationErrorService.commandErrors(cmd.errors as ValidationErrors, 'changePassword')
            logService.cloud.error "changePassword: Validation error: "+errorsMap.toString()
            render(status: 400, text: errorsMap as JSON)
        }
        else
        {
            result = userAdminService.resetPassword(cmd)
            if (result.status != PassFail.PASS) {
                render(status: 500, text: result.error)
            }
            else {
                logService.cloud.info("changePassword: success")
                render ""
            }
        }
    }

    @Secured(['ROLE_ADMIN'])
    def getAccounts()
    {
        ObjectCommandResponse response = cloudService.getAccounts()
        if (response.status != PassFail.PASS)
            render(status: 500, text: response.error)
        else
            render response.responseObject as JSON
    }

    @Secured(['ROLE_ADMIN'])
    def getVersion() {
        ObjectCommandResponse response = cloudService.getVersion()
        if (response.status != PassFail.PASS)
            render(status: 500, text: response.error)
        else
            render response.responseObject as JSON
    }

    @ControllerMethod
    @MessageMapping("/accountUpdates")
    @PreAuthorize("hasRole('ROLE_ADMIN'")
    @SendTo("/topic/accountUpdates")
    @Secured('hasRole("ROLE_ADMIN")')
    String accountUpdates(String message, Principal principal)
    {
        return "Account: "+message
    }
}
