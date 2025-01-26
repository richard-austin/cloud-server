package com.cloudwebapp.securingweb

import com.cloudwebapp.services.LogService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer

@Configuration
@ConfigurationProperties(prefix = "spring-security")
class ApplicationNoSecurity {
    @Autowired
    LogService logService
    boolean enabled  // Set in application(-dev).properties
    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        if(!enabled) {
            logService.cloud.info("============================================")
            logService.cloud.info("Spring Security is DISABLED!!!")
            logService.cloud.info("============================================")
            return (web) ->
                    web.ignoring().requestMatchers("/**")
        }
        else {
            logService.cloud.info("++++++++++++++++++++++++++++++++++++++++++++")
            logService.cloud.info("Spring Security is enabled")
            logService.cloud.info("++++++++++++++++++++++++++++++++++++++++++++")
            return (web) -> web.ignoring()
        }
    }
}
