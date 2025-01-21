package com.cloudwebapp.validators

import com.cloudwebapp.services.LogService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.validation.BindingResult
import org.springframework.validation.DataBinder
import org.springframework.validation.Validator

class GeneralValidator {
    Object target
    Validator validator
    BindingResult bindingResult

    GeneralValidator(Object target, Validator validator) {
        this.target = target
        this.validator = validator
    }

    BindingResult validate() {
        DataBinder binder = new DataBinder(target)
        binder.setValidator(validator)
        // validate the target object
        binder.validate()
        bindingResult = binder.getBindingResult()
        return bindingResult
    }

    ResponseEntity validationErrors(String restMethod, LogService logService) {
        logService.cloud.error restMethod+": Validation error: " + bindingResult.toString()
        def retVal = new BadRequestResult(bindingResult)
        return ResponseEntity
                .badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(retVal)
    }
}
