package com.cloudwebapp.security

import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.model.User
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.RememberMeAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException

class CloudRememberMeAuthenticationProvider extends RememberMeAuthenticationProvider{
    LogService logService
    CloudService cloudService
    UserRepository userRepository

    CloudRememberMeAuthenticationProvider(String key, LogService logService, CloudService cloudService, UserRepository userRepository) {
        super(key)
        this.logService = logService
        this.cloudService = cloudService
        this.userRepository = userRepository
    }

    @Override
    Authentication authenticate(Authentication authentication) throws AuthenticationException {

        if (!supports(authentication.getClass())) {
            return null
        }
        Authentication auth = super.authenticate(authentication)
        if(auth.authenticated) {
            String userName = authentication.getName()
            String productId = getProductId(userName)

            final boolean isAdmin = productId == "0000-0000-0000-0000"

            if (!isAdmin) {
                String cookie = cloudService.cloudListener.authenticate(productId)

                if (cookie == "" || cookie == "NO_CONN") {
                    logService.cloud.debug("Authentication failed: couldn't log onto NVR")
                    throw new BadCredentialsException(messages.getMessage(
                            "AbstractUserDetailsAuthenticationProvider.badCredentials",
                            "Unable to login to NVR"))
                } else
                // Save the NVRSESSIONID mapped against product ID to pass to the onAuthenticationSuccess handler
                    cloudService.authenticatedNVRs(productId, cookie)
            }
        }
        return auth
    }

    private String getProductId(String userName) {
        try {
            User user = userRepository.findByUsername(userName)

            return user.productid
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " exception in getProductId: " + ex.getMessage())
        }
        return ""
    }

    private void loginSuccess(String userName) {
        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}'")
    }

    private void logAudit(String auditType, GString message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }
}
