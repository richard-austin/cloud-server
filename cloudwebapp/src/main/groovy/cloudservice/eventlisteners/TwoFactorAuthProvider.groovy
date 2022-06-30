package cloudservice.eventlisteners

import cloudservice.User
import cloudwebapp.CloudService
import cloudwebapp.LogService
import grails.gorm.transactions.Transactional
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetails

class TwoFactorAuthProvider extends DaoAuthenticationProvider {
    LogService logService
    CloudService cloudService

    /**
     * additionalAuthenticationChecks:
     * @param userDetails
     * @param authentication
     * @throws AuthenticationException
     */
    protected void additionalAuthenticationChecks(UserDetails userDetails,
                                                  UsernamePasswordAuthenticationToken authentication)
            throws AuthenticationException {

        super.additionalAuthenticationChecks(userDetails, authentication)
        String userName = userDetails.getUsername()
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
