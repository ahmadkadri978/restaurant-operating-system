package com.restaurantqr.sse;

import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for PublicSseController.
 * Tests public SSE connection establishment, session verification,
 * restaurant validation, and emitter lifecycle.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PublicSseController Unit Tests")
class PublicSseControllerTest {

    @Mock private PublicMenuSseRegistry registry;
    @Mock private PublicSessionService publicSessionService;
    @Mock private RestaurantTableRepository tableRepository;

    private PublicSseController controller;

    private static final String QR_TOKEN = "test-qr-token";
    private static final String SESSION_CODE = "1234";
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        controller = new PublicSseController(registry, publicSessionService, tableRepository);
    }

    // ==================== Success ====================

    @Nested
    @DisplayName("Subscribe — Success")
    class SubscribeSuccess {

        @Test
        @DisplayName("Should return non-null SseEmitter on valid session")
        void shouldReturnEmitterOnValidSession() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            RestaurantTable table = createActiveTable();
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(table));
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            // When
            SseEmitter emitter = controller.subscribeToMenu(QR_TOKEN, SESSION_CODE);

            // Then
            assertThat(emitter).isNotNull();
        }

        @Test
        @DisplayName("Should verify session before establishing connection")
        void shouldVerifySessionFirst() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            RestaurantTable table = createActiveTable();
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(table));
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            // When
            controller.subscribeToMenu(QR_TOKEN, SESSION_CODE);

            // Then
            InOrder inOrder = inOrder(publicSessionService, tableRepository, registry);
            inOrder.verify(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            inOrder.verify(tableRepository).findActiveByQrTokenFetchRestaurant(QR_TOKEN);
            inOrder.verify(registry).register(eq(RESTAURANT_ID), any(SseEmitter.class));
        }

        @Test
        @DisplayName("Should register emitter with correct restaurantId")
        void shouldRegisterEmitterWithRestaurantId() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            RestaurantTable table = createActiveTable();
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(table));
            when(registry.getConnectionCount(RESTAURANT_ID)).thenReturn(1);

            // When
            controller.subscribeToMenu(QR_TOKEN, SESSION_CODE);

            // Then
            verify(registry).register(eq(RESTAURANT_ID), any(SseEmitter.class));
        }
    }

    // ==================== Validation Failures ====================

    @Nested
    @DisplayName("Subscribe — Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("Should throw when session verification fails")
        void shouldThrowWhenSessionVerificationFails() {
            // Given
            doThrow(new ResourceNotFoundException("Table not found"))
                    .when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);

            // When & Then
            assertThatThrownBy(() -> controller.subscribeToMenu(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Table not found");

            verify(registry, never()).register(anyLong(), any(SseEmitter.class));
        }

        @Test
        @DisplayName("Should throw when QR token not found in repository")
        void shouldThrowWhenQrTokenNotFound() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> controller.subscribeToMenu(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid or inactive QR token");

            verify(registry, never()).register(anyLong(), any(SseEmitter.class));
        }

        @Test
        @DisplayName("Should throw when restaurant is null")
        void shouldThrowWhenRestaurantIsNull() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            RestaurantTable table = new RestaurantTable();
            table.setRestaurant(null);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(table));

            // When & Then
            assertThatThrownBy(() -> controller.subscribeToMenu(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Restaurant not found or inactive");

            verify(registry, never()).register(anyLong(), any(SseEmitter.class));
        }

        @Test
        @DisplayName("Should throw when restaurant is inactive")
        void shouldThrowWhenRestaurantInactive() {
            // Given
            doNothing().when(publicSessionService).verifySessionOrThrow(QR_TOKEN, SESSION_CODE);
            Restaurant inactiveRestaurant = new Restaurant();
            inactiveRestaurant.setId(RESTAURANT_ID);
            inactiveRestaurant.setActive(false);
            RestaurantTable table = new RestaurantTable();
            table.setRestaurant(inactiveRestaurant);
            when(tableRepository.findActiveByQrTokenFetchRestaurant(QR_TOKEN)).thenReturn(Optional.of(table));

            // When & Then
            assertThatThrownBy(() -> controller.subscribeToMenu(QR_TOKEN, SESSION_CODE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Restaurant not found or inactive");

            verify(registry, never()).register(anyLong(), any(SseEmitter.class));
        }
    }

    // ==================== Helpers ====================

    private RestaurantTable createActiveTable() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Test Restaurant");
        restaurant.setActive(true);

        RestaurantTable table = new RestaurantTable();
        table.setId(1L);
        table.setTableNumber(5);
        table.setRestaurant(restaurant);
        table.setActive(true);
        return table;
    }
}

