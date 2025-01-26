package com.cloudwebapp.validators

import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.services.UtilsService
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import com.cloudwebapp.commands.AdminChangeEmailCommand

class AdminChangeEmailCommandValidator implements Validator{
    final UserRepository ur

    AdminChangeEmailCommandValidator(UserRepository ur) {
        this.ur = ur
    }

    @Override
    boolean supports(Class<?> clazz) {
        return AdminChangeEmailCommand.class == clazz
    }

    @Override
    void validate(Object target, Errors errors) {
        if(target instanceof AdminChangeEmailCommand) {
            if (NullOrBlank.isNullOrBlank(target.username))
                errors.rejectValue("username", "$target.username cannot be null or empty")
            else if (ur.findByUsername(target.username) == null)
                errors.rejectValue("username", "User $target.username does not exist")

            if(NullOrBlank.isNullOrBlank(target.email))
                errors.rejectValue("email", "email address cannot be null or empty")
            else if(ur.findByEmail(target.email) != null)
                errors.rejectValue("email", "Cannot use this email address")
            else if(!target.email.matches(UtilsService.emailRegex))
                errors.rejectValue("email", "Invalid email address")

            if(target.email != target.confirmEmail)
                errors.rejectValue("confirmEmail", "Email and confirmEmail do not match")
        }
    }
}
