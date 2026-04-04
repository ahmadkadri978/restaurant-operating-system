package com.restaurantqr.table;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for PublicSessionController.
 * Tests verify endpoint delegation, success path, and error propagation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicSessionController Unit Tests")
class PublicSessionControllerTest {

    @Mock
    private PublicSessionService publicSessionService;

    private PublicSessionController controller;

    private static final String QR_TOKEN = "test-qr-token";
    private static final String VALID_CODE = "1234";

    @BeforeEach
    void setUp() {
        controller = new PublicSessionController(publicSessionService);
    }

    // ==================== Verify — Success ====================

    @Nested
    @DisplayName("Verify — Success")
    class VerifySuccess {

        @Test
        @DisplayName("Should return verified=true on successful verification")
        void shouldReturnVerifiedTrue() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);

            VerifySessionResponse response = controller.verify(QR_TOKEN, new VerifySessionRequest(VALID_CODE));

            assertThat(response).isNotNull();
            assertThat(response.verified()).isTrue();
        }

        @Test
        @DisplayName("Should delegate qrToken and code to service")
        void shouldDelegateToService() {
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);

            controller.verify(QR_TOKEN, new VerifySessionRequest(VALID_CODE));

            verify(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);
        }
    }

    // ==================== Verify — Errors ====================

    @Nested
    @DisplayName("Verify — Errors")
    class VerifyErrors {

        @Test
        @DisplayName("Should propagate ResourceNotFoundException for invalid QR token")
        void shouldPropagateNotFoundForInvalidQrToken() {
            doThrow(new ResourceNotFoundException("Table not found"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);

            assertThatThrownBy(() -> controller.verify(QR_TOKEN, new VerifySessionRequest(VALID_CODE)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");
        }

        @Test
        @DisplayName("Should propagate BusinessRuleException for invalid code")
        void shouldPropagateBusinessRuleForInvalidCode() {
            String wrongCode = "9999";
            doThrow(new BusinessRuleException("Invalid session code"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, wrongCode);

            assertThatThrownBy(() -> controller.verify(QR_TOKEN, new VerifySessionRequest(wrongCode)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Invalid session code");
        }

        @Test
        @DisplayName("Should propagate BusinessRuleException when session not open")
        void shouldPropagateBusinessRuleWhenSessionNotOpen() {
            doThrow(new BusinessRuleException("Session is not open for this table"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);

            assertThatThrownBy(() -> controller.verify(QR_TOKEN, new VerifySessionRequest(VALID_CODE)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Session is not open");
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException when restaurant inactive")
        void shouldPropagateNotFoundWhenRestaurantInactive() {
            doThrow(new ResourceNotFoundException("Restaurant not found or inactive"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, VALID_CODE);

            assertThatThrownBy(() -> controller.verify(QR_TOKEN, new VerifySessionRequest(VALID_CODE)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Restaurant not found or inactive");
        }
    }
}

