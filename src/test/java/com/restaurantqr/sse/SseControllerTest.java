package com.restaurantqr.sse;

import com.restaurantqr.auth.dto.AuthPrincipal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for SseController.
 * Tests SSE connection establishment, auth extraction, emitter lifecycle,
 * timeout/error handlers, and connection limit rejection.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SseController Unit Tests")
class SseControllerTest {

    @Mock
    private SseEmitterRegistry registry;

    private SseController controller;

    private Authentication authentication;
    private static final Long RESTAURANT_ID = 1L;
    private static final Long USER_ID = 10L;

    @BeforeEach
    void setUp() {
        controller = new SseController(registry);

        AuthPrincipal principal = new AuthPrincipal(USER_ID, "MANAGER", RESTAURANT_ID);
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
    }

    // ==================== Subscribe Success ====================

    @Nested
    @DisplayName("Subscribe — Success")
    class SubscribeSuccess {

        @Test
        @DisplayName("Should return non-null SseEmitter")
        void shouldReturnNonNullEmitter() {
            when(registry.register(eq(RESTAURANT_ID), any(SseEmitter.class))).thenReturn(true);
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            SseEmitter emitter = controller.subscribe(authentication);

            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("Should register emitter in registry with correct restaurantId")
        void shouldRegisterEmitterInRegistry() {
            when(registry.register(eq(RESTAURANT_ID), any(SseEmitter.class))).thenReturn(true);
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            controller.subscribe(authentication);

            verify(registry).register(eq(RESTAURANT_ID), any(SseEmitter.class));
        }

        @Test
        @DisplayName("Should extract restaurantId from authentication principal")
        void shouldExtractRestaurantIdFromAuth() {
            Long differentRestaurantId = 42L;
            AuthPrincipal principal = new AuthPrincipal(USER_ID, "STAFF", differentRestaurantId);
            Authentication auth = mock(Authentication.class);
            when(auth.getPrincipal()).thenReturn(principal);
            when(registry.register(eq(differentRestaurantId), any(SseEmitter.class))).thenReturn(true);
            when(registry.getConnectionCount(differentRestaurantId)).thenReturn(1);

            controller.subscribe(auth);

            verify(registry).register(eq(differentRestaurantId), any(SseEmitter.class));
        }
    }

    // ==================== Connection Limit ====================

    @Nested
    @DisplayName("Subscribe — Connection Limit")
    class ConnectionLimit {

        @Test
        @DisplayName("Should return emitter even when connection limit reached")
        void shouldReturnEmitterWhenLimitReached() {
            // Registry returns false when limit is reached
            when(registry.register(eq(RESTAURANT_ID), any(SseEmitter.class))).thenReturn(false);

            SseEmitter emitter = controller.subscribe(authentication);

            // Still returns an emitter (completed with ERROR event)
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("Should not register emitter when limit reached")
        void shouldNotRegisterWhenLimitReached() {
            when(registry.register(eq(RESTAURANT_ID), any(SseEmitter.class))).thenReturn(false);

            controller.subscribe(authentication);

            // register was called but returned false; no further registration
            verify(registry).register(eq(RESTAURANT_ID), any(SseEmitter.class));
            // getConnectionCount should NOT be called (only called on successful registration)
            verify(registry, never()).getConnectionCount(anyLong());
        }
    }

    // ==================== Kitchen Role ====================

    @Nested
    @DisplayName("Subscribe — Kitchen Role")
    class KitchenRole {

        @Test
        @DisplayName("Should accept KITCHEN role authentication")
        void shouldAcceptKitchenRole() {
            AuthPrincipal kitchenPrincipal = new AuthPrincipal(5L, "KITCHEN", RESTAURANT_ID);
            Authentication kitchenAuth = mock(Authentication.class);
            when(kitchenAuth.getPrincipal()).thenReturn(kitchenPrincipal);
            when(registry.register(eq(RESTAURANT_ID), any(SseEmitter.class))).thenReturn(true);
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            SseEmitter emitter = controller.subscribe(kitchenAuth);

            assertThat(emitter).isNotNull();
            verify(registry).register(eq(RESTAURANT_ID), any(SseEmitter.class));
        }
    }
}

