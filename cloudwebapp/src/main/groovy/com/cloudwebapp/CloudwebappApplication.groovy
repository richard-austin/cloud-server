package com.cloudwebapp

import com.cloudwebapp.configuration.Config
import com.proxy.CloudProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

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
}
