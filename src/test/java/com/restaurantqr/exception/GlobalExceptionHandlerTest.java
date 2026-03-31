package com.restaurantqr.exception;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/test");
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404 NOT_FOUND")
    void shouldReturn404ForResourceNotFound() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Order not found: 42");

        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.message()).isEqualTo("Order not found: 42");
        assertThat(body.path()).isEqualTo("/api/v1/test");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("BusinessRuleException → 400 BAD_REQUEST")
    void shouldReturn400ForBusinessRule() {
        BusinessRuleException ex = new BusinessRuleException("Order items must not be empty");

        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessRule(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(body.message()).isEqualTo("Order items must not be empty");
    }

    @Test
    @DisplayName("RateLimitException → 429 TOO_MANY_REQUESTS")
    void shouldReturn429ForRateLimit() {
        RateLimitException ex = new RateLimitException("Rate limit exceeded");

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimit(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(429);
        assertThat(body.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("InvalidCredentialsException → 401 UNAUTHORIZED")
    void shouldReturn401ForInvalidCredentials() {
        InvalidCredentialsException ex = new InvalidCredentialsException("wrong username/password");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidCredentials(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.message()).isEqualTo("wrong username/password");
    }

    @Test
    @DisplayName("InvalidSessionCodeException → 400 BAD_REQUEST")
    void shouldReturn400ForInvalidSessionCode() {
        InvalidSessionCodeException ex = new InvalidSessionCodeException("Invalid session code");

        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidSessionCode(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("INVALID_SESSION_CODE");
    }

    @Test
    @DisplayName("Generic Exception → 500 INTERNAL_SERVER_ERROR")
    void shouldReturn500ForGenericException() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("Unexpected error occurred");
    }

    @Test
    @DisplayName("PessimisticLockException → 409 CONFLICT")
    void shouldReturn409ForPessimisticLock() {
        PessimisticLockException ex = new PessimisticLockException("Lock timeout");

        ResponseEntity<ApiErrorResponse> response = handler.handleLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.code()).isEqualTo("LOCK_TIMEOUT");
        assertThat(body.message()).isEqualTo("Table is busy, please retry");
    }

    @Test
    @DisplayName("LockTimeoutException → 409 CONFLICT")
    void shouldReturn409ForLockTimeout() {
        LockTimeoutException ex = new LockTimeoutException("Lock wait timeout");

        ResponseEntity<ApiErrorResponse> response = handler.handleLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("LOCK_TIMEOUT");
    }
}


