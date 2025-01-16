package com.cloudwebapp.validators

import com.cloudwebapp.commands.SetupSMTPAccountCommand
import com.cloudwebapp.services.UtilsService
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class SetupSMTPAccountCommandValidator implements Validator {
    @Override
    boolean supports(Class<?> clazz) {
        return false
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof SetupSMTPAccountCommand) {

            if (target.auth) {
                if (NullOrBlank.isNullOrBlank(target.username))
                    errors.rejectValue("username", "username is required");
                else if (target.username.size() > 50)
                    errors.rejectValue("username", "Maximum username length is 50 characters")
            }

            if (target.auth && NullOrBlank.isNullOrBlank(target.password))
                errors.rejectValue("password", "password is required");
            else if (target.password.size() > 50)
                errors.rejectValue("password", "Maximum password length is 50 characters")

            if (target.auth && target.password != target.confirmPassword)
                errors.rejectValue("confirmPassword", "password and confirmPassword must match")

            if (target.enableStartTLS) {
                Set<String> validProtocols = new HashSet(["TLSv1.2", "TLSv1.3"])
                if (!validProtocols.contains(target.sslProtocols))
                    errors.rejectValue("sslProtocols", "sslProtocols should be TLSv1.2 or TLSv1.3 if enableStartTLS is true")
            }

            if (target.enableStartTLS) {
                if (target.sslTrust == null || target.sslTrust == "")
                    errors.rejectValue("sslTrust", "sslTrust is required if enableStartTLS is true")

                if (NullOrBlank.isNullOrBlank(target.host))
                    errors.rejectValue("host", "host cannot be null or empty")
                else if (target.host.size() < 3 || target.host.size() > 120)
                    errors.rejectValue("host", "host url must be between 3 and 120 characters")
            }

            if (target.port < 1 || target.port > 65535)
                errors.rejectValue("port", "port must be between 1 and 65535")

            if (NullOrBlank.isNullOrBlank(target.fromAddress))
                errors.rejectValue("fromAddress", "fromAddress cannot be null or empty")
            else if (!target.fromAddress.matches(UtilsService.emailRegex))
                errors.rejectValue("fromAddress", "Email format is not valid")
        }
    }
}
