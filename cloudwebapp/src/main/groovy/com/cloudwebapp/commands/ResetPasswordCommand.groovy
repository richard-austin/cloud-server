package com.cloudwebapp.commands

import cloudwebapp.UtilsService
import grails.validation.Validateable

class ResetPasswordCommand implements Validateable{
    String newPassword
    String confirmNewPassword
    String uniqueId

    UtilsService utilsService

    static constraints = {
         newPassword(nullable: false, blank: false,
                validator: {newPassword, cmd ->
                    if(!newPassword.matches(cmd.utilsService.passwordRegex))
                        return "New password contains invalid characters or is too long (must be <= 32 characters)"
                })

        confirmNewPassword(validator: {confirmNewPassword, cmd ->
            if(confirmNewPassword != cmd.newPassword)
                return "New passwords do not match"
        })

        uniqueId(nullable: false, blank: false, maxSize: 212, minSize: 212)
    }

}
