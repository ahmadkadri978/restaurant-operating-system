package com.restaurantqr.exception;

public abstract  class ApiException extends RuntimeException{
    protected ApiException(String message) {
        super(message);
    }

    public abstract String errorCode();
}
