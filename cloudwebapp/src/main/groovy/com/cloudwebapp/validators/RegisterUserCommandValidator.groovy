package com.cloudwebapp.validators

import com.cloudwebapp.commands.RegisterUserCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.services.UtilsService
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class RegisterUserCommandValidator implements Validator {
    final UserRepository ur

    RegisterUserCommandValidator(UserRepository ur) {
        this.ur = ur
    }

    @Override
    boolean supports(Class<?> clazz) {
        return clazz == RegisterUserCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof RegisterUserCommand) {

            if (NullOrBlank.isNullOrBlank(target.username))
                errors.rejectValue("username", "username cannot be null or empty")
            else if (target.username.size() > 20)
                errors.rejectValue("username", "username must be 20 characters or less")
            else if (!target.username.matches(/^[a-zA-Z0-9](_(?!(.|_))|.(?!(_|.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$/))
                errors.rejectValue("username", "username format is invalid")

            if(NullOrBlank.isNullOrBlank(target.productId))
                errors.rejectValue("productId", "productId cannot be null or empty")
            else if(target.productId.size() != 19)
                errors.rejectValue("productId", "productId must be 19 characters length in total")
            else if(!target.productId.matches(/^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}/))
                errors.rejectValue("productId", "Product ID format is incorrect")

            if(NullOrBlank.isNullOrBlank(target.password))
                errors.rejectValue("password", "password cannot be null or empty")
            else if(target.password.size() > 20)
                errors.rejectValue("password", "password must have a maximum length of 20 characters")
            else if (!target.password.matches(UtilsService.passwordRegex))
                errors.rejectValue("password", "Password should contain alpha characters, numbers and special characters")

            if(target.confirmPassword != target.password)
                errors.rejectValue("confirmPassword", "confirmPassword should match password")

            if(NullOrBlank.isNullOrBlank(target.email))
                errors.rejectValue("email", "email cannot be null or blank")
            else if(!target.email.matches(UtilsService.emailRegex))
                errors.rejectValue("email", "email address is invalid")
            else if(ur.findByEmail(target.email) != null)
                errors.rejectValue("email", "Cannot use this email address")

            if(target.confirmEmail != target.email)
                errors.rejectValue("confirmEmail", "confirmEmail must match email address")
        }
    }
}
