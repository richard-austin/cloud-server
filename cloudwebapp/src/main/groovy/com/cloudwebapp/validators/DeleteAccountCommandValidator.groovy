package com.cloudwebapp.validators

import com.cloudwebapp.commands.DeleteAccountCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.model.User
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class DeleteAccountCommandValidator implements Validator{
    private final UserRepository userRepository
    DeleteAccountCommandValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    boolean supports(Class<?> clazz) {
        return clazz == DeleteAccountCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if(target instanceof DeleteAccountCommand) {
            if (NullOrBlank.isNullOrBlank(target.username))
                errors.rejectValue("userName", "userName cannot be null or empty")
            User user = userRepository.findByUsername(target.username)

            if (user == null)
                errors.rejectValue("userName", "user $target.username does not exist)")
        }
    }
}
