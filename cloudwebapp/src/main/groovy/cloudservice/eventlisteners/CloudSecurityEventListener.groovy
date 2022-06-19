package cloudservice.eventlisteners

import cloudservice.User
import cloudwebapp.CloudService
import cloudwebapp.LogService
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
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
    SpringSecurityService springSecurityService

    @Override
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Not used, but CloudSecurityEventListener will throw exceptions on logout unless it implements LogoutHandler
    }

    @Override
    void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String productId = getProductId(request.getParameter("username"))
        String userName = request.getParameter('username')
        final boolean isAdmin = getRole(userName).contains('ROLE_ADMIN')

        if(!isAdmin) {
            String cookie = cloudService.cloudListener.authenticate(productId)
            if (cookie != "" && cookie != "NO_CONN") {
                response.addHeader("Set-Cookie", "NVRSESSIONID=" + cookie + "; Path=/; HttpOnly")
                response.addHeader("Set-Cookie", "PRODUCTID=" + productId + "; Path=/; HttpOnly")
                response.getWriter().write('{"role": "ROLE_CLIENT"}')
                loginSuccess(userName)
            } else {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
                if (cookie == "")
                    response.getWriter().write("Failed to login to NVR")
                else
                    response.getWriter().write("Not connected to NVR")
                authentication.authenticated = false
                response.setHeader("Set-Cookie", "CLOUDSESSIONID=XYZ; Path=/; HttpOnly")
            }
        }
        else
            response.getWriter().write('{"role": "ROLE_ADMIN"}')
    }

    @Transactional
    private String getProductId(String userName)
    {
        try {
            User user = User.findByUsername(userName)

            return user.productid
        }
        catch(Exception ex)
        {
            logService.cloud.error(ex.getClass().getName() + " exception in getProductId: " + ex.getMessage())
        }
    }

    @Transactional
    private String[] getRole(String username)
    {
        try {
            String[] roles = springSecurityService.getPrincipal().getAuthorities()
            return roles
        }
        catch(Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " exception in getRole: " + ex.getMessage())
        }
    }

    private void loginSuccess(String userName) {
        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}'")
    }

    private void logAudit(String auditType, GString message) {
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
        cloudService.cloudListener.logoff(cookie)
    }

    @Override
    void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        def username = request.getParameter("username")
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.getWriter().write(exception.getMessage())

        loginFailure(username)
    }
}
