package com.restaurantqr.exception;

public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_CREDENTIALS";
    }
}
