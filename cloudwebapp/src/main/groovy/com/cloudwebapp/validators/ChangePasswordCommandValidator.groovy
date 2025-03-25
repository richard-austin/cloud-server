package com.cloudwebapp.validators

import com.cloudwebapp.commands.ChangePasswordCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.model.User
import com.cloudwebapp.services.UtilsService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class ChangePasswordCommandValidator implements Validator {
    UserRepository userRepository
    PasswordEncoder passwordEncoder

    ChangePasswordCommandValidator(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository
        this.passwordEncoder = passwordEncoder
    }

    @Override
    boolean supports(Class<?> clazz) {
        return clazz == ChangePasswordCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof ChangePasswordCommand) {
            if (NullOrBlank.isNullOrBlank(target.oldPassword))
                errors.rejectValue("oldPassword", "oldPassword cannot be null or empty")
            else {
                // Check the old password is correct
                Authentication auth = SecurityContextHolder.getContext().getAuthentication()
                def principal = auth.getPrincipal()
                if (principal) {
                    String userName = auth.getName()

                    User u = userRepository.findByUsername(userName)
                    if (!passwordEncoder.matches(target.oldPassword, u.password))
                        errors.rejectValue("oldPassword", "The old password given is incorrect")
                }
            }

            if (NullOrBlank.isNullOrBlank(target.newPassword))
                errors.rejectValue("newPassword", "newPassword cannot be null or empty")
            else if (!target.newPassword.matches(UtilsService.passwordRegex))
                errors.rejectValue("newPassword", "New password contains invalid characters or is too long (must be <= 32 characters)")

            if (target.confirmNewPassword != target.newPassword)
                errors.rejectValue("confirmNewPassword", "New passwords do not match")
        }
    }
}

