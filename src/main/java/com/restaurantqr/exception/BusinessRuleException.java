package com.restaurantqr.exception;

public class BusinessRuleException extends ApiException{
    public BusinessRuleException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "BUSINESS_RULE_VIOLATION";
    }
}
