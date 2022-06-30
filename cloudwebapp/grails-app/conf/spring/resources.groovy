package spring

import cloudservice.UserPasswordEncoderListener
import cloudservice.eventlisteners.CloudAuthFailEventListener
import cloudservice.eventlisteners.CloudRememberMeAuthenticationProvider
import cloudservice.eventlisteners.CloudSecurityEventListener
import cloudservice.eventlisteners.WebSocketConfiguration
import cloudservice.eventlisteners.TwoFactorAuthProvider
import com.proxy.CloudProperties
import grails.plugin.springsecurity.SpringSecurityUtils

// Place your Spring DSL code here
beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)

    cloudProxyProperties(CloudProperties) {
        grailsApplication = ref('grailsApplication')
    }

    if (grailsApplication.config.grails.plugin.springsecurity.active == true) {
        // This bean logs onto the NVR through the Cloud/CloudProxy link and audits user logins and logouts
        twoFactorAuthProvider(TwoFactorAuthProvider) {
            logService = ref("logService")
            cloudService = ref("cloudService")
            userDetailsService = ref('userDetailsService')
            passwordEncoder = ref('passwordEncoder')
            userCache = ref('userCache')
            preAuthenticationChecks = ref('preAuthenticationChecks')
            postAuthenticationChecks = ref('postAuthenticationChecks')
            authoritiesMapper = ref('authoritiesMapper')
            hideUserNotFoundExceptions = true
        }
        // This bean adds the PRODUCTID, and NVRSESSIONID cookies to the login response and audits login and logout events
        authenticationSuccessHandler(CloudSecurityEventListener) {
            logService = ref("logService")
            cloudService = ref("cloudService")
            springSecurityService = ref("springSecurityService")
        }

        // This bean audits failed user logins
        authenticationFailureHandler(CloudAuthFailEventListener) {
            logService = ref("logService")
            cloudService = ref("cloudService")
        }
        def conf = SpringSecurityUtils.securityConfig
        cloudRememberMeAuthenticationProvider(CloudRememberMeAuthenticationProvider, conf.rememberMe.key) {
            logService = ref("logService")
            cloudService = ref("cloudService")
        }
    }

    webSocketConfiguration(WebSocketConfiguration)

}
