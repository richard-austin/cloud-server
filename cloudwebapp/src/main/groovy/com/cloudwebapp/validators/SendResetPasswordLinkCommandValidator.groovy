package com.cloudwebapp.validators

import com.cloudwebapp.services.UtilsService
import jakarta.validation.constraints.Null
import org.springframework.validation.Errors
import org.springframework.validation.Validator
import com.cloudwebapp.commands.SendResetPasswordLinkCommand

class SendResetPasswordLinkCommandValidator implements Validator {
    @Override
    boolean supports(Class<?> clazz) {
        return clazz == SendResetPasswordLinkCommand.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof SendResetPasswordLinkCommand) {
            if (NullOrBlank.isNullOrBlank(target.email))
                errors.rejectValue("email", "email cannot be null or empty")
            else if (!target.email.matches(UtilsService.emailRegex))
                errors.rejectValue("email", "Invalid email address")

            if (NullOrBlank.isNullOrBlank(target.clientUri))
                errors.rejectValue("clientUri", "clientUri cannot be null or empty")
            else {
                try {
                    new URI(target.clientUri).toURL()
                }
                catch (URISyntaxException ignored) {
                    errors.rejectValue("clientUri", "Badly formed URL")
                }
            }
        }
    }
}
