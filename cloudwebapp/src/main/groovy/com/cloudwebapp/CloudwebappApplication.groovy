package com.cloudwebapp

import com.cloudwebapp.configuration.Config
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.security.CloudRememberMeAuthenticationProvider
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.proxy.CloudProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.RememberMeServices
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices

@SpringBootApplication
class CloudwebappApplication {

    static void main(String[] args) {
        SpringApplication.run(CloudwebappApplication, args)
    }

    @Autowired
    Config config

    @Bean
    CloudProperties cloudProperties() {
        return CloudProperties.Create(config)
    }

    @Bean
    RememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices.RememberMeTokenAlgorithm encodingAlgorithm = TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256
        TokenBasedRememberMeServices rememberMe = new TokenBasedRememberMeServices("evenMoreSupersecret", userDetailsService, encodingAlgorithm)
        rememberMe.setMatchingAlgorithm(TokenBasedRememberMeServices.RememberMeTokenAlgorithm.MD5)
        return rememberMe
    }

    @Bean
    CloudRememberMeAuthenticationProvider cloudRememberMeAuthenticationProvider(Config config, LogService logService, CloudService cloudService, UserRepository userRepository) {
        return new CloudRememberMeAuthenticationProvider("remembermekey", logService, cloudService, userRepository)
    }
}
