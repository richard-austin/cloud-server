package com.cloudwebapp

import com.cloudwebapp.beans.MyRememberMeServices

import com.cloudwebapp.configuration.Config
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.security.CloudRememberMeAuthenticationProvider
import com.cloudwebapp.services.CloudService
import com.cloudwebapp.services.LogService
import com.proxy.CloudProperties
import jakarta.annotation.PreDestroy
import jakarta.servlet.ServletContext
import jakarta.servlet.ServletException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.servlet.ServletContextInitializer
import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices

@SpringBootApplication
class CloudwebappApplication implements ServletContextInitializer {

    static void main(String[] args) {
        SpringApplication.run(CloudwebappApplication, args)
    }

    @Autowired
    Config config

    @Autowired
    LogService logService

    @Bean
    CloudProperties cloudProperties() {
        return CloudProperties.Create(config)
    }

    @Bean
    MyRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices.RememberMeTokenAlgorithm encodingAlgorithm = TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256
        MyRememberMeServices rememberMe = new MyRememberMeServices("evenMoreSupersecret", userDetailsService, encodingAlgorithm)
        rememberMe.setMatchingAlgorithm(TokenBasedRememberMeServices.RememberMeTokenAlgorithm.MD5)
        return rememberMe
    }

    @Bean
    CloudRememberMeAuthenticationProvider cloudRememberMeAuthenticationProvider(Config config, LogService logService, CloudService cloudService, UserRepository userRepository) {
        return new CloudRememberMeAuthenticationProvider("evenMoreSupersecret", logService, cloudService, userRepository)
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(11)
    }

    @PreDestroy
    void onExit() {
        try {
           // sc_processesService.stopProcesses()
            logService.cloud.info("Cloud Services have been shut down")
        } catch (Exception ex) {
            logService.cloud.error("${ex.getClass()} when shutting down services: ${ex.getMessage()}")
        }
    }

    @Override
    void onStartup(ServletContext servletContext) throws ServletException {
        servletContext.getSessionCookieConfig().setName("CLOUDSESSIONID")
    }
}
