package com.cloudwebapp.configuration

import com.cloudwebapp.IConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "quartz")
class Quartz {
    String autoStartup
}

@Configuration
@ConfigurationProperties(prefix = "mail.smtp")
class Mail {
    String configFile
}

@Configuration
@ConfigurationProperties(prefix="datasource")
class DataSource {
    String dbCreate
    String url
}

@Configuration
@ConfigurationProperties(prefix="cloud")
class Config implements IConfig {
    String appHomeDirectory
    String varHomeDirectory

    String mqTrustStorePath
    String username
    String password
    String mqURL
    String privateKeyPath
    String browserFacingPort
    String logLevel

    @Autowired
    Mail mail

    @Autowired
    DataSource dataSource

    @Autowired
    Quartz quartz
}
