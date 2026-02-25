package com.restaurantqr.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {
    public BusinessRuleException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "BUSINESS_RULE_VIOLATION";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
