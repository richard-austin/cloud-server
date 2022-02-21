package spring

import cloudservice.UserPasswordEncoderListener
import cloudservice.eventlisteners.CloudAuthFailEventListener
import cloudservice.eventlisteners.CloudSecurityEventListener
import com.proxy.CloudProperties

// Place your Spring DSL code here
beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)

    cloudProxyProperties(CloudProperties) {
        grailsApplication = ref('grailsApplication')
    }
    // This bean logs onto the NVR through the Cloud/CloudProxy link and audits user logins and logouts
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
}
