package cloudwebapp

import cloudwebappServices.CloudService
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class CloudServiceSpec extends Specification implements ServiceUnitTest<CloudService>{

    def setup() {
    }

    def cleanup() {
    }

    void "test something"() {
        expect:"fix me"
            true == false
    }
}
