package com.restaurantqr.exception;

public class InvalidSessionCodeException extends ApiException {
    public InvalidSessionCodeException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_SESSION_CODE";
    }
}
