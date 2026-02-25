package com.restaurantqr.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionSubclassesTest {

    @Test
    @DisplayName("BusinessRuleException - errorCode and httpStatus")
    void businessRuleException() {
        var ex = new BusinessRuleException("test message");
        assertThat(ex.errorCode()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("test message");
    }

    @Test
    @DisplayName("ResourceNotFoundException - errorCode and httpStatus")
    void resourceNotFoundException() {
        var ex = new ResourceNotFoundException("not found");
        assertThat(ex.errorCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getMessage()).isEqualTo("not found");
    }

    @Test
    @DisplayName("RateLimitException - errorCode and httpStatus")
    void rateLimitException() {
        var ex = new RateLimitException("too fast");
        assertThat(ex.errorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ex.getMessage()).isEqualTo("too fast");
    }

    @Test
    @DisplayName("InvalidCredentialsException - errorCode and httpStatus")
    void invalidCredentialsException() {
        var ex = new InvalidCredentialsException("bad creds");
        assertThat(ex.errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo("bad creds");
    }

    @Test
    @DisplayName("InvalidSessionCodeException - errorCode and httpStatus")
    void invalidSessionCodeException() {
        var ex = new InvalidSessionCodeException("bad code");
        assertThat(ex.errorCode()).isEqualTo("INVALID_SESSION_CODE");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).isEqualTo("bad code");
    }

    @Test
    @DisplayName("ForbiddenOperationException - errorCode and httpStatus")
    void forbiddenOperationException() {
        var ex = new ForbiddenOperationException("not allowed");
        assertThat(ex.errorCode()).isEqualTo("FORBIDDEN");
        assertThat(ex.httpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getMessage()).isEqualTo("not allowed");
    }
}
