package com.cloudwebapp.beans

import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.logout.LogoutHandler
import org.springframework.stereotype.Component


@Component
class CloudAuthFailureHandler implements AuthenticationFailureHandler, LogoutHandler{
    @Autowired
    LogService logService
    @Autowired
    CloudService cloudService

    @Override
    void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setContentType("application/json")
        response.setStatus(HttpStatus.UNAUTHORIZED.value())
        final String errorMessage = exception.getMessage()

        response.getWriter().write(errorMessage)
        def username = request.getParameter("username")
        loginFailure(username)
    }

    def loginFailure(String userName) {
        logAudit("USER-LOGIN-FAILURE", "user='${userName}")
    }

    private void logAudit(String auditType, def message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }

    @Override
    void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String userName = authentication?.principal?.username
        String cookie = request.getHeader("cookie")
        logAudit("USER-LOGOUT", "user='${userName}")
        cloudService.cloudListener.logoff(cookie)
    }

}

