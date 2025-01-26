package com.cloudwebapp.error

final class CloudRestMethodException extends Exception{
    String reason

    CloudRestMethodException(String message, String reason) {
        super(message)
        this.reason = reason
    }
}
