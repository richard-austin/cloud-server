package com.cloudwebapp.beans

import com.cloudwebapp.services.LogService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class ApplicationStartup
        implements ApplicationListener<ApplicationReadyEvent> {
//        @Autowired
//        OnvifService onvifService

    @Autowired
    LogService logService
    /**
     * This event is executed as late as conceivably possible to indicate that
     * the application is ready to service requests.
     */
    @Override
    void onApplicationEvent(final ApplicationReadyEvent event) {
//        sc_processesService.setOnvifService(onvifService)
//        sc_processesService.startProcesses()

        logService.cloud.info("Started Cloud Services!!!!!!!!!!!!!!")
    }
}
