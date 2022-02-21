package cloudwebapp

import cloudservice.commands.RegisterUserCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.validation.ValidationErrors

class CloudController {
    CloudService cloudService
    LogService logService
    ValidationErrorService validationErrorService
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
}
