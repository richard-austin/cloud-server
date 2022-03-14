package cloudservice.commands

import cloudservice.User
import cloudwebapp.UtilsService
import grails.validation.Validateable

class AdminChangeEmailCommand  implements Validateable{
    String username
    String email
    String confirmEmail

    UtilsService utilsService

    static constraints = {
        username(nullable: false, blank: false,
                validator: {username ->
                    User user = User.findByUsername(username)
                    if(user == null)
                        return "User ${username} does not exist"
                })

        email(nullable: false, blank: false,
                validator: {email, cmd ->
                    if(!email.matches(cmd.utilsService.emailRegex))
                        return "Invalid email address"
                })

        confirmEmail(validator: {confirmEmail, cmd ->
            if(confirmEmail != cmd.email)
                return "New email addresses do not match"
        })
    }
}
