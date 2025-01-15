package com.cloudwebapp.commands

import cloudservice.User
import cloudwebapp.UtilsService
import grails.plugin.springsecurity.SpringSecurityService
import grails.validation.Validateable
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken


class ChangeEmailCommand {
    String password
    String newEmail
    String confirmNewEmail
}
