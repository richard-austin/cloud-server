package cloudwebapp

import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import com.proxy.Cloud
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional

@Transactional
class CloudService {
    LogService logService
    GrailsApplication grailsApplication
    Cloud cloud = null

    def start() {
        if(cloud == null)
            cloud = new Cloud()

        ObjectCommandResponse response = new ObjectCommandResponse()
        try
        {
            cloud.start()
        }
        catch(Exception ex)
        {
            response.status= PassFail.FAIL
            response.error = "Exception in CloudProxy.start: "+ex.getClass().getName()+": "+ex.getMessage()
            logService.cloud.error(response.error)
        }
        return response
    }

    def stop() {
        if(cloud)
            cloud.stop()
    }
}
