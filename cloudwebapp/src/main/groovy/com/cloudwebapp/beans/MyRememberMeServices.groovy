package com.cloudwebapp.beans

import com.cloudwebapp.model.User
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.proxy.CloudMQ
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices

class MyRememberMeServices extends TokenBasedRememberMeServices {
    @Autowired
    LogService logService

    @Autowired
    CloudService cloudService

    MyRememberMeServices(String key, UserDetailsService userDetailsService) {
        super(key, userDetailsService)
    }
    MyRememberMeServices(String key, UserDetailsService userDetailsService,
                                        RememberMeTokenAlgorithm encodingAlgorithm) {
        super(key, userDetailsService)
    }

    @Override
    Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        def auth =  super.autoLogin(request, response)

        if(auth != null && auth.getPrincipal() instanceof User) {
            def principal = auth.getPrincipal()

            if (principal instanceof User) {
                def productId = principal.getProductid()

                if (productId != "0000-0000-0000-0000") {
                    String nvrSessionId = cloudService.cloudListener.authenticate(productId)

                    if (nvrSessionId == "" || nvrSessionId == "NO_CONN") {
                        logService.cloud.debug("Authentication failed: couldn't log onto NVR")
                        response.addHeader("Set-Cookie", "remember-me=; Path=/; HttpOnly")
                        throw new RememberMeAuthenticationException("Communication with NVR failed")
                    } else {
                        // Save the NVRSESSIONID mapped against product ID to pass to the onAuthenticationSuccess handler
                        cloudService.authenticatedNVRs(productId, nvrSessionId)
                        response.addHeader("Set-Cookie", "NVRSESSIONID=" + nvrSessionId + "; Path=/; HttpOnly")
                        response.addHeader("Set-Cookie", "PRODUCTID=" + productId + "; Path=/; HttpOnly")
                        }
                        logService.cloud.info("NVRSESSIONID and PRODUCTID set up")
                        loginSuccessAudit(principal.username)
                    }
                }
            }
        return auth
    }

    @Override
    void loginFail(HttpServletRequest request, HttpServletResponse response) {
        super.loginFail(request, response)
    }

    @Override
    void loginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication successfulAuthentication) {
        super.loginSuccess(request, response, successfulAuthentication)
    }

    private void loginSuccessAudit(String userName) {
        logAudit("USER-LOGIN_SUCCESS: ", "user='${userName}'")
    }

    private void logAudit(String auditType, GString message) {
        logService.cloud.info "Audit:${auditType}- ${message.toString()}"
    }

}
