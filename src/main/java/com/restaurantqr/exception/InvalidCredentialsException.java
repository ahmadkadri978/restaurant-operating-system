package com.restaurantqr.exception;
import org.springframework.http.HttpStatus;
public class InvalidCredentialsException extends ApiException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
    @Override
    public String errorCode() {
        return "INVALID_CREDENTIALS";
    }
    @Override
    public HttpStatus httpStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
