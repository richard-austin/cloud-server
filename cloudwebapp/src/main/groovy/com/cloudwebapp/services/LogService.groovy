package com.cloudwebapp.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import jakarta.annotation.*

class LogService {

    Logger cloud = null
    static Logger logger = null

    void setLogLevel(String level)
    {
        cloud.setLevel(level=='INFO' ? Level.INFO :
                     level=='DEBUG' ? Level.DEBUG :
                     level=='TRACE' ? Level.TRACE :
                     level=='WARN' ? Level.WARN :
                     level=='ERROR' ? Level.ERROR :
                     level=='OFF' ? Level.OFF :
                     level=='ALL' ? Level.ALL : Level.OFF)
    }

    LogService() {
        cloud = (Logger) LoggerFactory.getLogger('CLOUD')
        LogService.logger = cloud
    }

    @PostConstruct
    def initialise() {
        setLogLevel('DEBUG')
    }
}
