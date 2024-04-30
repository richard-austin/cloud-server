package cloudservice.commands

import grails.validation.Validateable

class AddOrUpdateActiveMQCredsCmd implements Validateable {
    String username
    String password
    String confirmPassword
    String mqHost

    public static final activeMQPasswordRegex = /^$|^[A-Za-z0-9]{20}$/
    public static final usernameRegex = /^$|^[a-zA-Z0-9](_(?!(.|_))|.(?!(_|.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$/
    public static final hostNameRegex = /^[a-zA-Z0-9][a-zA-Z0-9._-]*$/
    public static final ipV4RegEx = /^([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))\.([0-9]|[1-9][0-9]|1([0-9][0-9])|2([0-4][0-9]|5[0-5]))$/
    public static final ipV6RegEx = /^s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]d|1dd|[1-9]?d)(.(25[0-5]|2[0-4]d|1dd|[1-9]?d)){3}))|:)))(%.+)?s*(\/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8]))?$/

    static constraints = {
        username(nullable: true, blank: true,
                validator: { username, cmd ->
                    if (username == null)
                        username = cmd.username = ""
                    if (!username.matches(usernameRegex) && username != "")
                        return "Format or length of username is incorrect"
                })
        password(nullable: true, blank: true,
                validator: { password, cmd ->
                    if (password == null)
                        password = cmd.password = ""
                    if (!password.matches(activeMQPasswordRegex) && password != "")
                        return "Password is invalid"

                    if (cmd.username == "" && password != "")
                        return "Password must be blank if username is blank"
                    else if (cmd.username != "" && password == "")
                        return "Password cannot be blank if username is not blank"
                })
        confirmPassword(nullable: true, blank: true,
                validator: { confirmPassword, cmd ->
                    if (confirmPassword == null)
                        confirmPassword = cmd.confirmPassword = ""
                    if (confirmPassword != cmd.password)
                        return "Password and confirm password do not match"
                })
        mqHost(nullable: false, blank: false,
                validator: { mqHost, cmd ->
                    if (!mqHost.matches(hostNameRegex) &&
                            !mqHost.matches(ipV4RegEx) &&
                            !mqHost.matches(ipV6RegEx)) {
                        return "Host name is invalid"
                    }
                })
    }
}
