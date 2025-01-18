package com.cloudwebapp.beans

import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component


@Component
class CloudAuthFailureHandler implements AuthenticationFailureHandler{
    @Override
    void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        response.setContentType("application/json")
        response.setStatus(HttpStatus.UNAUTHORIZED.value())
        final String errorMessage = exception.getMessage()

        response.getWriter().write(errorMessage)
    }
}

