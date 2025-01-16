package com.cloudwebapp.validators

import com.cloudwebapp.commands.SetAccountEnabledStatusCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.model.User
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class SetAccountEnabledStatusCommandValidator implements Validator {
    private final UserRepository userRepository

    SetAccountEnabledStatusCommandValidator(final UserRepository userRepository) {
        this.userRepository = userRepository
    }


    @Override
    boolean supports(Class<?> clazz) {
        return clazz == SetAccountEnabledStatusCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof SetAccountEnabledStatusCommand) {
            if (NullOrBlank.isNullOrBlank(target.username))
                errors.rejectValue("username", "username cannot be null or blank")
            User user = userRepository.findByUsername(target.username)

            if (user == null)
                errors.rejectValue("username", "Unknown user ${target.username}")
        }
    }
}
