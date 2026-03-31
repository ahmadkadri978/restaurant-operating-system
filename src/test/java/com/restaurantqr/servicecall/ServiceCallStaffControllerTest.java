package com.restaurantqr.servicecall;

import com.restaurantqr.auth.dto.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ServiceCallStaffController Unit Tests")
class ServiceCallStaffControllerTest {

    @Mock private ServiceCallService serviceCallService;
    @Mock private PushTokenService pushTokenService;
    @Mock private Authentication authentication;

    private ServiceCallStaffController controller;

    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new ServiceCallStaffController(serviceCallService, pushTokenService);
        AuthPrincipal principal = new AuthPrincipal(USER_ID, "STAFF", RESTAURANT_ID);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== getActiveCalls ====================

    @Nested
    @DisplayName("Get Active Calls")
    class GetActiveCallsTests {

        @Test
        @DisplayName("Should return active calls on initial load")
        void shouldReturnActiveCallsInitial() {
            ServiceCallResponse call = new ServiceCallResponse(1L, 5, CallType.WAITER, Instant.now());
            when(serviceCallService.getActiveCalls(RESTAURANT_ID, null)).thenReturn(List.of(call));

            List<ServiceCallResponse> result = controller.getActiveCalls(authentication, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo(1L);
            verify(serviceCallService).getActiveCalls(RESTAURANT_ID, null);
        }

        @Test
        @DisplayName("Should pass since parameter for polling")
        void shouldPassSinceParameter() {
            Instant since = Instant.now().minusSeconds(10);
            when(serviceCallService.getActiveCalls(RESTAURANT_ID, since)).thenReturn(List.of());

            List<ServiceCallResponse> result = controller.getActiveCalls(authentication, since);

            assertThat(result).isEmpty();
            verify(serviceCallService).getActiveCalls(RESTAURANT_ID, since);
        }
    }

    // ==================== savePushToken ====================

    @Nested
    @DisplayName("Save Push Token")
    class SavePushTokenTests {

        @Test
        @DisplayName("Should delegate to pushTokenService with correct parameters")
        void shouldDelegateToPushTokenService() {
            SavePushTokenRequest request = new SavePushTokenRequest("fcm-token-abc123");

            controller.savePushToken(authentication, request);

            verify(pushTokenService).saveToken(RESTAURANT_ID, USER_ID, "fcm-token-abc123");
        }
    }

    // ==================== removePushToken ====================

    @Nested
    @DisplayName("Remove Push Token")
    class RemovePushTokenTests {

        @Test
        @DisplayName("Should delegate to pushTokenService with correct parameters")
        void shouldDelegateToPushTokenService() {
            controller.removePushToken(authentication, "fcm-token-xyz789");

            verify(pushTokenService).removeToken(USER_ID, "fcm-token-xyz789");
        }
    }
}

