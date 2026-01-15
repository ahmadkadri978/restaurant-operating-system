package com.restaurantqr.exception;

public class ResourceNotFoundException extends ApiException{
    public ResourceNotFoundException(String message) {
        super(message);
    }

    @Override
    public String errorCode() {
        return "RESOURCE_NOT_FOUND";
    }
}
