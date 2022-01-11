package spring

import cloudservice.UserPasswordEncoderListener
import com.proxy.CloudProperties

// Place your Spring DSL code here
beans = {
    userPasswordEncoderListener(UserPasswordEncoderListener)

    cloudProxyProperties(CloudProperties) {
        grailsApplication = ref('grailsApplication')
    }

}
