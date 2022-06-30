package cloudservice.eventlisteners

import cloudservice.User
import cloudwebapp.CloudService
import cloudwebapp.LogService
import grails.gorm.transactions.Transactional
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.RememberMeAuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException

class CloudRememberMeAuthenticationProvider extends RememberMeAuthenticationProvider{
    LogService logService
    CloudService cloudService


    CloudRememberMeAuthenticationProvider(String key) {
        super(key)
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

    @Transactional
    private String getProductId(String userName) {
        try {
            User user = User.findByUsername(userName)

            return user.productid
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " exception in getProductId: " + ex.getMessage())
        }
        return ""
    }

    @Transactional
    private void loginSuccess(String userName) {
        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}'")
    }

    private void logAudit(String auditType, GString message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }
}
