package com.cloudwebapp.interfaceobjects

import com.cloudwebapp.enums.RestfulResponseStatusEnum

class RestfulResponse {
    RestfulResponseStatusEnum status
    Integer responseCode
    boolean userError = false
    String errorMsg
    def responseObject  // deserialised data from the Lumax

    String toString() {
        return """
status         : ${status}
responseCode   : ${responseCode}
userError      : ${userError}
errorMsg       : ${errorMsg}
responseObject : ${responseObject}
"""
    }
}
