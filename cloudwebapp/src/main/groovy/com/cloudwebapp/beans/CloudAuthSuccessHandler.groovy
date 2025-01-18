package com.cloudwebapp.beans

import com.google.gson.GsonBuilder
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

class Authority {
    String authority
}

@Component
class CloudAuthSuccessHandler implements AuthenticationSuccessHandler {
    void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        response.setContentType("application/json")
        ArrayList<Authority> auths = new ArrayList<>()

        authentication.authorities.forEach {
            auths.add(new Authority(authority: it))
        }

        def gson = new GsonBuilder().create()
        response.getWriter().write(gson.toJson(auths))
     }
}
