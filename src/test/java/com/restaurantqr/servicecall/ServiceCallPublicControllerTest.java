package com.restaurantqr.servicecall;

import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.RateLimitException;
import com.restaurantqr.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceCallPublicController Unit Tests")
class ServiceCallPublicControllerTest {

    @Mock private ServiceCallService serviceCallService;

    private ServiceCallPublicController controller;

    private static final String QR_TOKEN = "QR-TOKEN-1";
    private static final String SESSION_CODE = "1234";

    @BeforeEach
    void setUp() {
        controller = new ServiceCallPublicController(serviceCallService);
    }

    // ==================== createCall ====================

    @Nested
    @DisplayName("Create Service Call")
    class CreateCallTests {

        @Test
        @DisplayName("Should return 201 CREATED on success")
        void shouldReturn201OnSuccess() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            ServiceCallResponse expected = new ServiceCallResponse(1L, 5, CallType.WAITER, Instant.now());
            when(serviceCallService.createCall(QR_TOKEN, SESSION_CODE, request)).thenReturn(expected);

            ResponseEntity<ServiceCallResponse> response = controller.createCall(QR_TOKEN, SESSION_CODE, request);

            assertThat(response.getStatusCode().value()).isEqualTo(201);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().id()).isEqualTo(1L);
            assertThat(response.getBody().tableNumber()).isEqualTo(5);
            assertThat(response.getBody().callType()).isEqualTo(CallType.WAITER);
        }

        @Test
        @DisplayName("Should delegate to service with correct parameters")
        void shouldDelegateToService() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.BILL);
            ServiceCallResponse expected = new ServiceCallResponse(2L, 3, CallType.BILL, Instant.now());
            when(serviceCallService.createCall(QR_TOKEN, SESSION_CODE, request)).thenReturn(expected);

            controller.createCall(QR_TOKEN, SESSION_CODE, request);

            verify(serviceCallService).createCall(QR_TOKEN, SESSION_CODE, request);
        }

        @Test
        @DisplayName("Should propagate RateLimitException from service")
        void shouldPropagateRateLimitException() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(serviceCallService.createCall(any(), any(), any()))
                    .thenThrow(new RateLimitException("Cooldown active"));

            assertThatThrownBy(() -> controller.createCall(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(RateLimitException.class);
        }

        @Test
        @DisplayName("Should propagate ResourceNotFoundException from service")
        void shouldPropagateNotFound() {
            CreateServiceCallRequest request = new CreateServiceCallRequest(CallType.WAITER);
            when(serviceCallService.createCall(any(), any(), any()))
                    .thenThrow(new ResourceNotFoundException("Invalid QR token"));

            assertThatThrownBy(() -> controller.createCall(QR_TOKEN, SESSION_CODE, request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ==================== getCooldownStatus ====================

    @Nested
    @DisplayName("Get Cooldown Status")
    class GetCooldownStatusTests {

        @Test
        @DisplayName("Should return 200 with active cooldown")
        void shouldReturn200ActiveCooldown() {
            CooldownStatusResponse expected = new CooldownStatusResponse(
                    true, 120, CallType.WAITER, Instant.now().minusSeconds(60));
            when(serviceCallService.getCooldownStatus(QR_TOKEN, SESSION_CODE)).thenReturn(expected);

            ResponseEntity<CooldownStatusResponse> response = controller.getCooldownStatus(QR_TOKEN, SESSION_CODE);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().active()).isTrue();
            assertThat(response.getBody().cooldownRemainingSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("Should return 200 with inactive cooldown")
        void shouldReturn200InactiveCooldown() {
            CooldownStatusResponse expected = new CooldownStatusResponse(false, 0, null, null);
            when(serviceCallService.getCooldownStatus(QR_TOKEN, SESSION_CODE)).thenReturn(expected);

            ResponseEntity<CooldownStatusResponse> response = controller.getCooldownStatus(QR_TOKEN, SESSION_CODE);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().active()).isFalse();
            assertThat(response.getBody().cooldownRemainingSeconds()).isZero();
        }

        @Test
        @DisplayName("Should propagate BusinessRuleException from service")
        void shouldPropagateBusinessRuleException() {
            when(serviceCallService.getCooldownStatus(any(), any()))
                    .thenThrow(new BusinessRuleException("Invalid session"));

            assertThatThrownBy(() -> controller.getCooldownStatus(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}

