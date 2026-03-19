package com.restaurantqr.sse;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for SSE Components.
 * Tests SseEmitterRegistry, RestaurantEventPublisher, and SSE payloads.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SSE Component Unit Tests")
class SseComponentsTest {

    // ==================== SseEmitterRegistry Tests ====================

    @Nested
    @DisplayName("SseEmitterRegistry Tests")
    class SseEmitterRegistryTests {

        private SseEmitterRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new SseEmitterRegistry();
        }

        @Test
        @DisplayName("Should register and track emitter for restaurant")
        void shouldRegisterEmitter() {
            // Given
            SseEmitter emitter = new SseEmitter();
            Long restaurantId = 1L;

            // When
            registry.register(restaurantId, emitter);

            // Then
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(1);
            assertThat(registry.getTotalConnectionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should track multiple emitters for same restaurant")
        void shouldTrackMultipleEmittersForSameRestaurant() {
            // Given
            Long restaurantId = 1L;
            SseEmitter emitter1 = new SseEmitter();
            SseEmitter emitter2 = new SseEmitter();
            SseEmitter emitter3 = new SseEmitter();

            // When
            registry.register(restaurantId, emitter1);
            registry.register(restaurantId, emitter2);
            registry.register(restaurantId, emitter3);

            // Then
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(3);
        }

        @Test
        @DisplayName("Should track emitters for different restaurants separately")
        void shouldTrackEmittersPerRestaurant() {
            // Given
            SseEmitter emitter1 = new SseEmitter();
            SseEmitter emitter2 = new SseEmitter();

            // When
            registry.register(1L, emitter1);
            registry.register(2L, emitter2);

            // Then
            assertThat(registry.getConnectionCount(1L)).isEqualTo(1);
            assertThat(registry.getConnectionCount(2L)).isEqualTo(1);
            assertThat(registry.getTotalConnectionCount()).isEqualTo(2);
            assertThat(registry.getActiveRestaurantIds()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("Should remove emitter from registry")
        void shouldRemoveEmitter() {
            // Given
            Long restaurantId = 1L;
            SseEmitter emitter = new SseEmitter();
            registry.register(restaurantId, emitter);

            // When
            registry.remove(restaurantId, emitter);

            // Then
            assertThat(registry.getConnectionCount(restaurantId)).isEqualTo(0);
        }

        @Test
        @DisplayName("Should return 0 for unknown restaurant")
        void shouldReturnZeroForUnknownRestaurant() {
            // When & Then
            assertThat(registry.getConnectionCount(999L)).isEqualTo(0);
        }

        @Test
        @DisplayName("Should clean up empty restaurant lists")
        void shouldCleanupEmptyLists() {
            // Given
            Long restaurantId = 1L;
            SseEmitter emitter = new SseEmitter();
            registry.register(restaurantId, emitter);

            // When
            registry.remove(restaurantId, emitter);

            // Then
            assertThat(registry.getActiveRestaurantIds()).doesNotContain(restaurantId);
        }

        @Test
        @DisplayName("Should handle remove for non-existent emitter gracefully")
        void shouldHandleRemoveForNonExistentEmitter() {
            // Given
            SseEmitter emitter = new SseEmitter();

            // When & Then - should not throw
            assertThatCode(() -> registry.remove(1L, emitter)).doesNotThrowAnyException();
        }
    }

    // ==================== RestaurantEventPublisher Tests ====================

    @Nested
    @DisplayName("RestaurantEventPublisher Tests")
    class RestaurantEventPublisherTests {

        @Mock
        private SseEmitterRegistry registry;

        @Mock
        private PublicMenuSseRegistry publicMenuRegistry;

        private RestaurantEventPublisher publisher;

        @BeforeEach
        void setUp() {
            publisher = new RestaurantEventPublisher(registry, publicMenuRegistry);
        }

        @Test
        @DisplayName("Should publish ORDER_CREATED event with correct payload")
        void shouldPublishOrderCreatedEvent() {
            // Given
            Long restaurantId = 1L;
            OrderCreatedPayload payload = new OrderCreatedPayload(
                    100L, 15, 5, new BigDecimal("25.00"), "Test note", Instant.now()
            );

            // When
            publisher.publishOrderCreated(restaurantId, payload);

            // Then
            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
            verify(registry).broadcast(eq(restaurantId), eventCaptor.capture());

            SseEvent<?> event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo(SseEventType.ORDER_CREATED);
            assertThat(event.restaurantId()).isEqualTo(restaurantId);
            assertThat(event.payload()).isEqualTo(payload);
            assertThat(event.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should publish ORDER_SENT_TO_KITCHEN event")
        void shouldPublishOrderSentToKitchenEvent() {
            // Given
            Long restaurantId = 2L;
            OrderStatusPayload payload = new OrderStatusPayload(
                    200L, 20, 3, com.restaurantqr.order.staff.OrderStatus.SENT_TO_KITCHEN,
                    Instant.now(), null, null
            );

            // When
            publisher.publishOrderSentToKitchen(restaurantId, payload);

            // Then
            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
            verify(registry).broadcast(eq(restaurantId), eventCaptor.capture());

            assertThat(eventCaptor.getValue().type()).isEqualTo(SseEventType.ORDER_SENT_TO_KITCHEN);
        }

        @Test
        @DisplayName("Should publish ORDER_DONE event")
        void shouldPublishOrderDoneEvent() {
            // Given
            Long restaurantId = 3L;
            OrderStatusPayload payload = new OrderStatusPayload(
                    300L, 30, 7, com.restaurantqr.order.staff.OrderStatus.DONE,
                    Instant.now().minusSeconds(300), Instant.now(), null
            );

            // When
            publisher.publishOrderDone(restaurantId, payload);

            // Then
            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
            verify(registry).broadcast(eq(restaurantId), eventCaptor.capture());

            assertThat(eventCaptor.getValue().type()).isEqualTo(SseEventType.ORDER_DONE);
        }

        @Test
        @DisplayName("Should publish ORDER_CANCELLED event")
        void shouldPublishOrderCancelledEvent() {
            // Given
            Long restaurantId = 4L;
            OrderStatusPayload payload = new OrderStatusPayload(
                    400L, 40, 2, com.restaurantqr.order.staff.OrderStatus.CANCELLED,
                    null, null, Instant.now()
            );

            // When
            publisher.publishOrderCancelled(restaurantId, payload);

            // Then
            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
            verify(registry).broadcast(eq(restaurantId), eventCaptor.capture());

            assertThat(eventCaptor.getValue().type()).isEqualTo(SseEventType.ORDER_CANCELLED);
        }

        @Test
        @DisplayName("Should publish SERVICE_CALL event")
        void shouldPublishServiceCallEvent() {
            // Given
            Long restaurantId = 5L;
            ServiceCallPayload payload = new ServiceCallPayload(
                    500L, 8, com.restaurantqr.servicecall.CallType.WAITER, Instant.now()
            );

            // When
            publisher.publishServiceCall(restaurantId, payload);

            // Then
            ArgumentCaptor<SseEvent<?>> eventCaptor = ArgumentCaptor.forClass(SseEvent.class);
            verify(registry).broadcast(eq(restaurantId), eventCaptor.capture());

            SseEvent<?> event = eventCaptor.getValue();
            assertThat(event.type()).isEqualTo(SseEventType.SERVICE_CALL);
            assertThat(event.payload()).isEqualTo(payload);
        }
    }

    // ==================== SseEvent Record Tests ====================

    @Nested
    @DisplayName("SseEvent Record Tests")
    class SseEventRecordTests {

        @Test
        @DisplayName("Should create event with factory method")
        void shouldCreateEventWithFactoryMethod() {
            // Given
            OrderCreatedPayload payload = new OrderCreatedPayload(
                    100L, 10, 5, BigDecimal.TEN, null, Instant.now()
            );

            // When
            SseEvent<OrderCreatedPayload> event = SseEvent.of(
                    SseEventType.ORDER_CREATED, 1L, payload
            );

            // Then
            assertThat(event.type()).isEqualTo(SseEventType.ORDER_CREATED);
            assertThat(event.restaurantId()).isEqualTo(1L);
            assertThat(event.payload()).isEqualTo(payload);
            assertThat(event.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should create heartbeat event")
        void shouldCreateHeartbeatEvent() {
            // When
            SseEvent<Void> heartbeat = SseEvent.heartbeat(42L);

            // Then
            assertThat(heartbeat.type()).isEqualTo(SseEventType.HEARTBEAT);
            assertThat(heartbeat.restaurantId()).isEqualTo(42L);
            assertThat(heartbeat.payload()).isNull();
            assertThat(heartbeat.timestamp()).isNotNull();
        }

        @Test
        @DisplayName("Should create connected event")
        void shouldCreateConnectedEvent() {
            // When
            SseEvent<String> connected = SseEvent.connected(99L);

            // Then
            assertThat(connected.type()).isEqualTo(SseEventType.CONNECTED);
            assertThat(connected.restaurantId()).isEqualTo(99L);
            assertThat(connected.payload()).isEqualTo("Connected to SSE stream");
        }
    }

    // ==================== Payload Record Tests ====================

    @Nested
    @DisplayName("Payload Record Tests")
    class PayloadRecordTests {

        @Test
        @DisplayName("OrderCreatedPayload should hold all fields")
        void orderCreatedPayloadShouldHoldAllFields() {
            // Given
            Long orderId = 100L;
            Integer displayOrderNumber = 15;
            int tableNumber = 5;
            BigDecimal totalAmount = new BigDecimal("99.99");
            String note = "Special instructions";
            Instant createdAt = Instant.now();

            // When
            OrderCreatedPayload payload = new OrderCreatedPayload(
                    orderId, displayOrderNumber, tableNumber, totalAmount, note, createdAt
            );

            // Then
            assertThat(payload.orderId()).isEqualTo(orderId);
            assertThat(payload.displayOrderNumber()).isEqualTo(displayOrderNumber);
            assertThat(payload.tableNumber()).isEqualTo(tableNumber);
            assertThat(payload.totalAmount()).isEqualTo(totalAmount);
            assertThat(payload.note()).isEqualTo(note);
            assertThat(payload.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("OrderStatusPayload should hold all fields")
        void orderStatusPayloadShouldHoldAllFields() {
            // Given
            Long orderId = 200L;
            Integer displayOrderNumber = 25;
            int tableNumber = 3;
            com.restaurantqr.order.staff.OrderStatus status = 
                    com.restaurantqr.order.staff.OrderStatus.DONE;
            Instant sentToKitchenAt = Instant.now().minusSeconds(600);
            Instant doneAt = Instant.now();

            // When
            OrderStatusPayload payload = new OrderStatusPayload(
                    orderId, displayOrderNumber, tableNumber, status, sentToKitchenAt, doneAt, null
            );

            // Then
            assertThat(payload.orderId()).isEqualTo(orderId);
            assertThat(payload.displayOrderNumber()).isEqualTo(displayOrderNumber);
            assertThat(payload.tableNumber()).isEqualTo(tableNumber);
            assertThat(payload.status()).isEqualTo(status);
            assertThat(payload.sentToKitchenAt()).isEqualTo(sentToKitchenAt);
            assertThat(payload.doneAt()).isEqualTo(doneAt);
            assertThat(payload.cancelledAt()).isNull();
        }

        @Test
        @DisplayName("ServiceCallPayload should hold all fields")
        void serviceCallPayloadShouldHoldAllFields() {
            // Given
            Long callId = 300L;
            int tableNumber = 7;
            com.restaurantqr.servicecall.CallType callType = 
                    com.restaurantqr.servicecall.CallType.BILL;
            Instant createdAt = Instant.now();

            // When
            ServiceCallPayload payload = new ServiceCallPayload(
                    callId, tableNumber, callType, createdAt
            );

            // Then
            assertThat(payload.callId()).isEqualTo(callId);
            assertThat(payload.tableNumber()).isEqualTo(tableNumber);
            assertThat(payload.callType()).isEqualTo(callType);
            assertThat(payload.createdAt()).isEqualTo(createdAt);
        }
    }

    // ==================== SseEventType Enum Tests ====================

    @Nested
    @DisplayName("SseEventType Enum Tests")
    class SseEventTypeTests {

        @Test
        @DisplayName("Should have all expected event types")
        void shouldHaveAllExpectedEventTypes() {
            // Then
            assertThat(SseEventType.values()).containsExactlyInAnyOrder(
                    SseEventType.ORDER_CREATED,
                    SseEventType.ORDER_SENT_TO_KITCHEN,
                    SseEventType.ORDER_DONE,
                    SseEventType.ORDER_CANCELLED,
                    SseEventType.SERVICE_CALL,
                    SseEventType.MENU_ITEM_UPDATED,
                    SseEventType.MENU_ITEM_DELETED,
                    SseEventType.HEARTBEAT,
                    SseEventType.CONNECTED
            );
        }

        @Test
        @DisplayName("Event type names should be uppercase for SSE compatibility")
        void eventTypeNamesShouldBeUppercase() {
            for (SseEventType type : SseEventType.values()) {
                assertThat(type.name()).isEqualTo(type.name().toUpperCase());
            }
        }
    }
}


