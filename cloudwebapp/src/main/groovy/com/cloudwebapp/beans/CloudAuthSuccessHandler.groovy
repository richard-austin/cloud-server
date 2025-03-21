package com.cloudwebapp.beans

import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.model.User
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.google.gson.GsonBuilder
import com.proxy.CloudMQ
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

class Authority {
    String authority
}

@Component
class CloudAuthSuccessHandler implements AuthenticationSuccessHandler {
    @Autowired
    LogService logService

    @Autowired
    CloudService cloudService

    @Autowired
    UserRepository userRepository

    void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        final String userName = authentication.getPrincipal().getUsername()
        String productId = getProductId(userName)
        final boolean isAdmin = getRole().contains('ROLE_ADMIN')

        if (!isAdmin) {
            String nvrSessionId = cloudService.authenticatedNVRs(productId)

            String uri = request.getRequestURI()
            // If uri is /login/authenticate, we are here due to manual login and the 302 redirect would mess up the login
            if(uri != "/login/authenticate") {
                // Set up redirect to the original url if we are here due to remember me
                response.status = 302
                String url = request.getRequestURL().toString()
                response.addHeader("Location", "${url}")
            }

            response.addHeader("Set-Cookie", "NVRSESSIONID=" + nvrSessionId + "; Path=/; HttpOnly")
            response.addHeader("Set-Cookie", "PRODUCTID=" + productId + "; Path=/; HttpOnly")
            loginSuccess(userName)
        } else {
        //    response.getWriter().write('[{"authority": "ROLE_ADMIN"}]')
        }
        response.setContentType("application/json")
        ArrayList<Authority> auths = new ArrayList<>()

        authentication.authorities.forEach {
            auths.add(new Authority(authority: it))
        }

        def gson = new GsonBuilder().create()
        response.getWriter().write(gson.toJson(auths))
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

    private String[] getRole() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication()
            String[] roles = auth.getAuthorities()
            return roles
        }
        catch (Exception ex) {
            logService.cloud.error(ex.getClass().getName() + " exception in getRole: " + ex.getMessage())
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
