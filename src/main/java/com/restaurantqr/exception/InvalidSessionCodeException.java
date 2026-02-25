package com.restaurantqr.exception;

import org.springframework.http.HttpStatus;

public class InvalidSessionCodeException extends ApiException {
    public InvalidSessionCodeException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "INVALID_SESSION_CODE";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
