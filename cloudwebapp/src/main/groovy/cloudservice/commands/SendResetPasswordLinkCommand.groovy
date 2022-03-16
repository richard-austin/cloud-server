package cloudservice.commands

import cloudwebapp.UtilsService
import grails.validation.Validateable

class SendResetPasswordLinkCommand implements Validateable {
    String email
    UtilsService utilsService

    static constraints = {
        email(nullable: false, blank: false,
                validator: { email, cmd ->
                    if (!email.matches(cmd.utilsService.emailRegex))
                        return "Invalid email address"
                })
    }
}
