package cloudservice.eventlisteners

import cloudwebapp.CloudService
import cloudwebapp.LogService
import org.springframework.context.ApplicationListener
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.security.web.authentication.logout.LogoutHandler

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * CloudSecurityEventListener: Bean to log successful login events and logouts.
 */
class CloudSecurityEventListener implements LogoutHandler, AuthenticationSuccessHandler{
    LogService logService
    CloudService cloudService
//    void onApplicationEvent(AuthenticationSuccessEvent event) {
//        loginSuccess(event?.authentication?.principal?.username as String)
//    }
//
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutAction(authentication?.principal?.username as String)
    }

//    def loginSuccess(String userName) {
//        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}")

//    }
//
    def logoutAction(String userName) {
        logAudit("USER-LOGOUT", "user='${userName}")
        cloudService.cloud.logoff()
        cloudService.cloud.reset()
    }

    private void logAudit(String auditType, def message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }

    @Override
    void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        def x = response
        String cookie = cloudService.cloud.authenticate()
        if(cookie != "") {
            response.setHeader("Set-Cookie", "NVRSESSIONID="+cookie+"; Path=/; HttpOnly")
        }
    }
}

/**
 * CloudAuthFailEventListener: Bean to log unsuccessful log in events
 */
class CloudAuthFailEventListener implements ApplicationListener<AuthenticationFailureBadCredentialsEvent>,  LogoutHandler
{
    def logService
    void onApplicationEvent(AuthenticationFailureBadCredentialsEvent event) {

        loginFailure(event?.authentication?.principal as String)
    }

    def loginFailure(String userName) {
        logAudit("USER-LOGIN-FAILURE", "user='${userName}")
    }

    private void logAudit(String auditType, def message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }

    @Override
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Do nothing, it's just included as it's expected to be here and an exception occurs without it.
    }
}
