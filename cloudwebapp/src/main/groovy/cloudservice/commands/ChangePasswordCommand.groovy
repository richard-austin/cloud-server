package cloudservice.commands

import cloudwebapp.UtilsService
import grails.plugin.springsecurity.SpringSecurityService
import grails.validation.Validateable
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken

class ChangePasswordCommand implements Validateable{
    String oldPassword
    String newPassword
    String confirmNewPassword
    SpringSecurityService springSecurityService
    def authenticationManager

    UtilsService utilsService

    static constraints = {
        oldPassword(nullable: false, blank: false,
        validator: {oldPassword , cmd ->
            // Check the old password is correct
            def principal = cmd.springSecurityService.getPrincipal()
            String userName = principal.getUsername()

            boolean valid = true
            try {
                cmd.authenticationManager.authenticate new UsernamePasswordAuthenticationToken(userName, oldPassword)
            }
            catch (BadCredentialsException e) {
                valid = false
            }

            if(!valid /*!passwordEncoder.matches(oldPassword, pw)*/)
                return "The old password given is incorrect"
        })

        newPassword(nullable: false, blank: false,
        validator: {newPassword, cmd ->
            if(!newPassword.matches(cmd.utilsService.passwordRegex))
                return "New password contains invalid characters or is too long (must be <= 32 characters)"
        })

        confirmNewPassword(validator: {confirmNewPassword, cmd ->
            if(confirmNewPassword != cmd.newPassword)
                return "New passwords do not match"
        })
    }
}
