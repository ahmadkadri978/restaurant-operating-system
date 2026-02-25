package com.restaurantqr.exception;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/test");

    // ─── ApiException subclasses → unified handler ─────────────────────

    @Test
    @DisplayName("handleApiException (ResourceNotFoundException) → 404 NOT_FOUND")
    void handleApiException_resourceNotFound_returns404() {
        var ex = new ResourceNotFoundException("User not found");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.code()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(body.message()).isEqualTo("User not found");
        assertThat(body.path()).isEqualTo("/api/v1/test");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("handleApiException (BusinessRuleException) → 400 BAD_REQUEST")
    void handleApiException_businessRule_returns400() {
        var ex = new BusinessRuleException("Username 'staff1' is already taken");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
        assertThat(body.message()).isEqualTo("Username 'staff1' is already taken");
        assertThat(body.path()).isEqualTo("/api/v1/test");
    }

    @Test
    @DisplayName("handleApiException (RateLimitException) → 429 TOO_MANY_REQUESTS")
    void handleApiException_rateLimit_returns429() {
        var ex = new RateLimitException("Only one order per minute");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(429);
        assertThat(body.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(body.message()).isEqualTo("Only one order per minute");
    }

    @Test
    @DisplayName("handleApiException (InvalidCredentialsException) → 401 UNAUTHORIZED")
    void handleApiException_invalidCredentials_returns401() {
        var ex = new InvalidCredentialsException("wrong username/password");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(401);
        assertThat(body.error()).isEqualTo("Unauthorized");
        assertThat(body.code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.message()).isEqualTo("wrong username/password");
    }

    @Test
    @DisplayName("handleApiException (InvalidSessionCodeException) → 400 BAD_REQUEST")
    void handleApiException_invalidSessionCode_returns400() {
        var ex = new InvalidSessionCodeException("Invalid session code");

        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("INVALID_SESSION_CODE");
        assertThat(body.message()).isEqualTo("Invalid session code");
    }

    // ─── MethodArgumentNotValidException → 400 ───────────────────────────

    @Test
    @DisplayName("handleValidation → 400 with VALIDATION_ERROR code and field message")
    void handleValidation_returns400WithFieldMessage() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "username", "username is required"));
        var methodParam = new MethodParameter(String.class.getMethod("toString"), -1);
        var ex = new MethodArgumentNotValidException(methodParam, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.message()).isEqualTo("username: username is required");
    }

    @Test
    @DisplayName("handleValidation → 400 with fallback message when no field errors")
    void handleValidation_noFieldErrors_returnsFallbackMessage() throws NoSuchMethodException {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        var methodParam = new MethodParameter(String.class.getMethod("toString"), -1);
        var ex = new MethodArgumentNotValidException(methodParam, bindingResult);

        ResponseEntity<ApiErrorResponse> response = handler.handleValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation error");
    }

    // ─── DataIntegrityViolationException → 409 ───────────────────────────

    @Test
    @DisplayName("handleDataIntegrity → 409 CONFLICT with DUPLICATE_ENTRY code")
    void handleDataIntegrity_returns409() {
        var ex = new DataIntegrityViolationException("Duplicate entry 'staff1' for key 'username'");

        ResponseEntity<ApiErrorResponse> response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.error()).isEqualTo("Conflict");
        assertThat(body.code()).isEqualTo("DUPLICATE_ENTRY");
        assertThat(body.message()).isEqualTo("A record with the given details already exists");
        assertThat(body.path()).isEqualTo("/api/v1/test");
    }

    // ─── PessimisticLockException / LockTimeoutException → 409 ��─────────

    @Test
    @DisplayName("handleLock (PessimisticLockException) → 409 CONFLICT with LOCK_TIMEOUT code")
    void handleLock_pessimistic_returns409() {
        var ex = new PessimisticLockException();

        ResponseEntity<ApiErrorResponse> response = handler.handleLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("LOCK_TIMEOUT");
        assertThat(body.message()).isEqualTo("Table is busy, please retry");
    }

    @Test
    @DisplayName("handleLock (LockTimeoutException) → 409 CONFLICT with LOCK_TIMEOUT code")
    void handleLock_timeout_returns409() {
        var ex = new LockTimeoutException();

        ResponseEntity<ApiErrorResponse> response = handler.handleLock(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("LOCK_TIMEOUT");
    }

    // ─── Generic Exception → 500 ────────────────────────────────────────

    @Test
    @DisplayName("handleGeneric → 500 INTERNAL_SERVER_ERROR with INTERNAL_ERROR code")
    void handleGeneric_returns500() {
        var ex = new RuntimeException("Something broke");

        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.error()).isEqualTo("Internal Server Error");
        assertThat(body.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.message()).isEqualTo("Unexpected error occurred");
        assertThat(body.path()).isEqualTo("/api/v1/test");
    }
}

