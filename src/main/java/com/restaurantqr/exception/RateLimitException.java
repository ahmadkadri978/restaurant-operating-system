package com.restaurantqr.exception;

public class RateLimitException extends ApiException {

    public RateLimitException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "RATE_LIMIT_EXCEEDED";
    }
}
