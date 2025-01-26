package com.cloudwebapp.validators

import com.cloudwebapp.commands.AdminChangePasswordCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.services.UtilsService
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class AdminChangePasswordCommandValidator implements Validator {
    private final UserRepository userRepository

    AdminChangePasswordCommandValidator(UserRepository ur) {
        userRepository = ur
    }

    @Override
    boolean supports(Class<?> clazz) {
        return clazz == AdminChangePasswordCommand
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof AdminChangePasswordCommand) {
            if (NullOrBlank.isNullOrBlank(target.username))
                errors.rejectValue("username", "username cannot be null or blank")
            else if (userRepository.findByUsername(target.username) == null)
                errors.rejectValue("username", "User ${target.username} does not exist")

            if(NullOrBlank.isNullOrBlank(target.password))
                errors.rejectValue("password", "password cannot be null or empty")
            else if(!target.password.matches(UtilsService.passwordRegex))
                errors.rejectValue("password", "New password contains invalid characters or is too long (must be <= 32 characters)")

            if (target.confirmPassword != target.password)
                errors.rejectValue("confirmPassword","New passwords do not match")
        }
    }
}



