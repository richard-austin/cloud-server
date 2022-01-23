package cloudservice.eventlisteners

import cloudwebapp.CloudService
import cloudwebapp.LogService
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
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

    @Override
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Not used, but CloudSecurityEventListener will throw exceptions on logout unless it implements LogoutHandler
    }

    @Override
    void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String cookie = cloudService.cloud.authenticate()
        if(cookie != "" && cookie != "NO_CONN") {
            response.addHeader("Set-Cookie", "NVRSESSIONID="+cookie+"; Path=/; HttpOnly")
            loginSuccess(request.getParameter("username"))
        }
        else
        {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            if(cookie == "")
                response.getWriter().write("Failed to login to NVR")
            else
                response.getWriter().write("Not connected to NVR")
        }
    }

    private void loginSuccess(String userName) {
        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}'")
    }

    private void logAudit(String auditType, def message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }
}

/**
 * CloudAuthFailEventListener: Bean to log unsuccessful log in events
 */
class CloudAuthFailEventListener implements AuthenticationFailureHandler,  LogoutHandler
{
    LogService logService
    CloudService cloudService

    def loginFailure(String userName) {
        logAudit("USER-LOGIN-FAILURE", "user='${userName}")
    }

    private void logAudit(String auditType, def message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }

    @Override
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
 //       response.setStatus(HttpServletResponse.SC_OK)
        String userName = authentication?.principal?.username
        String cookie = request.getHeader("cookie")
        logAudit("USER-LOGOUT", "user='${userName}")
        cloudService.cloud.logoff(cookie)
    }

    @Override
    void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        def username = request.getParameter("username")
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.getWriter().write(exception.getMessage())

        loginFailure(username)
    }
}
