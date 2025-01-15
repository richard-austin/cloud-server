package com.cloudwebapp.commands

import cloudservice.User
import grails.validation.Validateable

class SetAccountEnabledStatusCommand implements Validateable{
    boolean accountEnabled
    String username

    static constraints = {
        accountEnabled (nullable: false, inList: [true, false])
        username (nullable: false, blank: false,
        validator: {username ->
            User user = User.findByUsername(username)

            if(user == null)
                return "Unknown user ${username}"
        })
    }
}
