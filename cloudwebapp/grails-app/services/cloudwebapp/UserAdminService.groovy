package cloudwebapp

import cloudservice.User
import cloudservice.commands.ResetPasswordCommand
import cloudservice.commands.SetAccountEnabledStatusCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService

@Transactional()
class UserAdminService {
    SpringSecurityService springSecurityService
    LogService logService
    UserService userService

    ObjectCommandResponse resetPassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            def principal = springSecurityService.getPrincipal()
            String userName = principal.getUsername()

            User user = User.findByUsername(userName)
            user.setPassword(cmd.getNewPassword())
            user.save()
        }
        catch(Exception ex)
        {
            logService.cloud.error("Exception in resetPassword: "+ex.getCause()+ ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }

        return result
    }

    /**
     * setAccountEnabledStatus: Enable/disable a user account
     * @param cmd: Command object containing the username and the enabled status
     * @return: Response object
     */
    ObjectCommandResponse setAccountEnabledStatus(SetAccountEnabledStatusCommand cmd) {
        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
            User user = userService.findByUsername(cmd.username)
            if(user != null)
            {
                user.setEnabled(cmd.accountEnabled)
                userService.save(user)
            }
            else
            {
                response.status = PassFail.FAIL
                response.error ="Could not find user ${cmd.username}"
                logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
            }
        }
        catch(Exception ex)
        {
            response.status = PassFail.FAIL
            response.error ="${ex.getClass().getName()} in setAccountEnabledStatus: ${ex.getMessage()}"
            logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
        }
        return response
    }

}
