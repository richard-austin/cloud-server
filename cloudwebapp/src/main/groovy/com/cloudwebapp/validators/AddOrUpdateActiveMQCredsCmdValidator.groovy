package com.cloudwebapp.validators

import com.cloudwebapp.commands.AddOrUpdateActiveMQCredsCmd
import org.springframework.validation.Errors
import org.springframework.validation.Validator

class AddOrUpdateActiveMQCredsCmdValidator implements Validator {
    public static final activeMQPasswordRegex = /^$|^[A-Za-z0-9]{20}$/
    public static final usernameRegex = /^$|^[a-zA-Z0-9](_(?!(.|_))|.(?!(_|.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$/
    public static final hostNameRegex = /^[a-zA-Z0-9][a-zA-Z0-9._-]*$/
    public static final ipV4RegEx = /^([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))$/
    public static final ipV6RegEx = /^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$/


    @Override
    boolean supports(Class<?> clazz) {
        return clazz == AddOrUpdateActiveMQCredsCmd.class
    }

    @Override
    void validate(Object target, Errors errors) {
        if (target instanceof AddOrUpdateActiveMQCredsCmd) {
            if (target.username == null)
                target.username = ""
            if (!target.username.matches(usernameRegex) && target.username != "")
                errors.rejectValue("username", "Format or length of username is incorrect")

            if (target.password == null)
                target.password = ""
            if (!target.password.matches(activeMQPasswordRegex) && target.password != "")
                errors.rejectValue("password", "Password is invalid")

            if (target.username == "" && target.password != "")
                errors.rejectValue("password", "Password must be blank if username is blank")
            else if (target.username != "" && target.password == "")
                errors.rejectValue("username", "Password cannot be blank if username is not blank")

            if (target.confirmPassword == null)
                target.confirmPassword = target.confirmPassword = ""
            if (target.confirmPassword != target.password)
                errors.rejectValue("confirmPassword", "Password and confirm password do not match")

            if (target.mqHost == null || target.mqHost.isEmpty() || target.mqHost.isBlank())
                errors.rejectValue("mqHost", "mqHost cannot be null or empty")
            if (!target.mqHost.matches(hostNameRegex) &&
                    !target.mqHost.matches(ipV4RegEx) &&
                    !target.mqHost.matches(ipV6RegEx)) {
                errors.rejectValue("mqHost", "Host name is invalid")
            }
        }
    }
}
