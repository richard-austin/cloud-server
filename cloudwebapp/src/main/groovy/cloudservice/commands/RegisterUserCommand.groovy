package cloudservice.commands

import grails.validation.Validateable

class RegisterUserCommand implements Validateable {
    String username
    String productId
    String password
    String confirmPassword
    String email
    String confirmEmail

    static constraints = {
        username (nullable: false, blank: false, maxSize: 20,
        validator: {username ->
            if(!username.matches(/^[a-zA-Z0-9](_(?!(.|_))|.(?!(_|.))|[a-zA-Z0-9]){3,18}[a-zA-Z0-9]$/))
                return "username format is invalid"
        })
        productId (nullable: false, blank: false, maxSize: 19, minSize: 19,
        validator: {productId -> {
            if(!productId.matches(/^(?:[A-Z0-9]{4}-){3}[A-Z0-9]{4}/))
                return "Product ID format is incorrect"
        }})
        password (nullable: false, blank: false, maxSize: 25,
        validator: {password -> {
            if(!password.matches(/^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,}$/))
                return "Password should contain alpha characters, numbers and special characters"
        }})
        confirmPassword (
                validator: {confirmPassword, cmd -> {
                    if(confirmPassword != cmd.password)
                        return "confirmPassword should match password"
                }}
        )
        email (nullable: false, blank: false, maxSize: 40,
        validator: {email -> {
            if(!email.matches(/^([a-zA-Z0-9_\-\.]+)@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.)|(([a-zA-Z0-9\-]+\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\]?)$/))
                return "email address is invalid"
        }})
        confirmEmail (
            validator: {confirmEmail, cmd -> {
                if(confirmEmail != cmd.email)
                    return "confirmEmail should match email address"
            }}
        )
    }
}
