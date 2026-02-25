package com.restaurantqr.exception;

import org.springframework.http.HttpStatus;

public class RateLimitException extends ApiException {

    public RateLimitException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_EXCEEDED";
    }

    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
