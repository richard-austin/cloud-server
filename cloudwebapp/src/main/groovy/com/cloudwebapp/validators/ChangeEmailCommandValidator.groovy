package com.cloudwebapp.validators

import com.cloudwebapp.commands.ChangeEmailCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.services.UtilsService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class ChangeEmailCommandValidator implements Validator {
    private final AuthenticationManager authenticationManager
    private final UserRepository userRepository

    ChangeEmailCommandValidator(AuthenticationManager authenticationManager, UserRepository userRepository) {
        this.authenticationManager = authenticationManager
        this.userRepository = userRepository
    }

    @Override
    boolean supports(Class<?> clazz) {
        return clazz == ChangeEmailCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof ChangeEmailCommand) {
            if (NullOrBlank.isNullOrBlank(target.password))
                errors.rejectValue("password", "password cannot be null or empty")
            else {
                // Check the old password is correct
                Authentication auth = SecurityContextHolder.getContext().getAuthentication()
                def principal = auth.getPrincipal()
                if (principal) {   // No principal in dev mode
                    String userName = auth.getName()

                    try {
                        authenticationManager.authenticate new UsernamePasswordAuthenticationToken(userName, target.password)
                    }
                    catch (BadCredentialsException ignored) {
                        errors.rejectValue("password", "The password is incorrect")
                    }
                }
            }

            if (NullOrBlank.isNullOrBlank(target.newEmail))
                errors.rejectValue("newEmail", "newEmail cannot be null or blank")
            else if (userRepository.findByEmail(target.newEmail) != null)
                errors.rejectValue("newEmail", "Cannot use this email address")
            else if (!target.newEmail.matches(UtilsService.emailRegex))
                errors.rejectValue("newEmail", "Email address is not in the correct format")

            if (target.newEmail != target.confirmNewEmail)
                errors.rejectValue("confirmNewEmail", "email and confirmEmail must match")
        }
    }
}
