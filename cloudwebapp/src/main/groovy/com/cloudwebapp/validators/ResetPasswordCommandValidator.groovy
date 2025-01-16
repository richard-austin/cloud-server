package com.cloudwebapp.validators

import com.cloudwebapp.commands.ResetPasswordCommand
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import com.cloudwebapp.services.UtilsService

class ResetPasswordCommandValidator implements Validator {
    @Override
    boolean supports(Class<?> clazz) {
        return clazz == ResetPasswordCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof ResetPasswordCommand) {
            if (NullOrBlank.isNullOrBlank(target.newPassword))
                errors.rejectValue("newPassword", "newPassword cannot be null or empty")
            else if (!UtilsService.newPassword.matches(cmd.utilsService.passwordRegex))
                errors.rejectValue("newPassword", "New password contains invalid characters or is too long (must be <= 32 characters)")
            if (target.confirmNewPassword != target.newPassword)
                errors.rejectValue("confirmNewPassword", "New passwords do not match")

            if (NullOrBlank.isNullOrBlank(target.uniqueId))
                errors.rejectValue("uniquId", "uniqueId cannot be null or empty")
            else if (target.uniqueId.size() != 212)
                errors.rejectValue("uniqueId", "uniqueId must be exactly 212 characters in lengtrh.")
        }
    }
}
