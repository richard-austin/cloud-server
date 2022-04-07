package cloudservice.commands

import cloudservice.User
import grails.validation.Validateable

class DeleteAccountCommand implements Validateable{
    String username

    static constraints = {
        username(nullable: false, blank: false,
                 validator: {username ->
                     User user = User.findByUsername(username)

                     if(user == null)
                         return "User ${username} does not exist"
                 })
    }
}
