package cloudservice.entityobjects

enum RestfulResponseStatusEnum {
    PASS('Pass'),													// Restful Service completed successfully
    FAIL('An undefined error occurred'),							// An undefined error occurred
    CERTIFICATE_ERROR('Certificate error'),							// Certificate error when setting up the trust manager
    CONNECT_FAIL('Connect failure'),								// Could not connect to this host, bad URL or attempt timed out
    SERVICE_NOT_FOUND('This service could not be found'),			// Requested service could not be found on the server
    ERROR_RESPONSE('Bad response code from the Lumax'),				// a response code other than 200 was received
    JSON_PARSING_ERROR('The data from the Lumax is invalid') 		// Could not parse the Json document returned by the Lumax

    private String name
    private RestfulResponseStatusEnum(String name){
        this.name = name
    }

    public String toString() {
        return name
    }
}
