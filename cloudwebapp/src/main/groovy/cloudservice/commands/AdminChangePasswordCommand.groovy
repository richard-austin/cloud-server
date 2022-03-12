package cloudservice.commands

import cloudservice.User
import cloudwebapp.UtilsService
import grails.validation.Validateable

class AdminChangePasswordCommand implements Validateable{
    String username
    String password
    String confirmPassword

    UtilsService utilsService

    static constraints = {
        username(nullable: false, blank: false,
                validator: {username ->
                    User user = User.findByUsername(username)
                    if(user == null)
                        return "User ${username} does not exist"
                })

        password(nullable: false, blank: false,
                validator: {password, cmd ->
                    if(!password.matches(cmd.utilsService.passwordRegex))
                        return "New password contains invalid characters or is too long (must be <= 64 characters)"
                })

        confirmPassword(validator: {confirmPassword, cmd ->
            if(confirmPassword != cmd.password)
                return "New passwords do not match"
        })
    }
}
