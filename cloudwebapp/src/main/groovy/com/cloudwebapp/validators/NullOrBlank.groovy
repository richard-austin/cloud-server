package com.cloudwebapp.validators

class NullOrBlank {
    static boolean isNullOrBlank(String test) {
        return test == null || test.isEmpty() || test.isBlank()
    }
}
