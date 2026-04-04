package com.restaurantqr.sse.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.servicecall.CallType;
import com.restaurantqr.sse.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Phase B Redis pub/sub SSE bridge components.
 * All tests are Mockito-based — no Spring context, no Redis required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Redis SSE Bridge Tests")
class RedisBridgeTest {

    /** Shared ObjectMapper matching production configuration. */
    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return om;
    }

    // ==================== Test Data Builders ====================

    static OrderCreatedPayload sampleOrderCreatedPayload() {
        return new OrderCreatedPayload(101L, 5, 3, new BigDecimal("29.50"), "no onions", Instant.parse("2026-03-27T10:15:30Z"));
    }

    static OrderStatusPayload sampleOrderStatusPayload(OrderStatus status) {
        return new OrderStatusPayload(101L, 5, 3, status,
                Instant.parse("2026-03-27T10:16:00Z"),
                status == OrderStatus.DONE ? Instant.parse("2026-03-27T10:20:00Z") : null,
                status == OrderStatus.CANCELLED ? Instant.parse("2026-03-27T10:18:00Z") : null);
    }

    static ServiceCallPayload sampleServiceCallPayload() {
        return new ServiceCallPayload(201L, 7, CallType.WAITER, Instant.parse("2026-03-27T10:25:00Z"));
    }

    static MenuItemUpdatedPayload sampleMenuItemUpdatedPayload() {
        return new MenuItemUpdatedPayload(301L, 10L, "Hummus", "Creamy chickpea dip",
                new BigDecimal("5.99"), "/uploads/hummus.jpg", 1711533000000L, true, true, 1);
    }

    static MenuItemDeletedPayload sampleMenuItemDeletedPayload() {
        return new MenuItemDeletedPayload(302L, 10L);
    }

    /** Provides all 7 event types with matching sample payloads. */
    static Stream<Arguments> allEventTypesWithPayloads() {
        return Stream.of(
                Arguments.of(SseEventType.ORDER_CREATED, sampleOrderCreatedPayload()),
                Arguments.of(SseEventType.ORDER_SENT_TO_KITCHEN, sampleOrderStatusPayload(OrderStatus.SENT_TO_KITCHEN)),
                Arguments.of(SseEventType.ORDER_DONE, sampleOrderStatusPayload(OrderStatus.DONE)),
                Arguments.of(SseEventType.ORDER_CANCELLED, sampleOrderStatusPayload(OrderStatus.CANCELLED)),
                Arguments.of(SseEventType.SERVICE_CALL, sampleServiceCallPayload()),
                Arguments.of(SseEventType.MENU_ITEM_UPDATED, sampleMenuItemUpdatedPayload()),
                Arguments.of(SseEventType.MENU_ITEM_DELETED, sampleMenuItemDeletedPayload())
        );
    }

    // ==================== RedisEventBridge Tests ====================

    @Nested
    @DisplayName("RedisEventBridge — publish to Redis")
    class RedisEventBridgeTests {

        @Mock
        private StringRedisTemplate redisTemplate;

        private RedisEventBridge bridge;

        @BeforeEach
        void setUp() {
            bridge = new RedisEventBridge(redisTemplate, "test-instance-1", new SimpleMeterRegistry());
        }

        @Test
        @DisplayName("Should serialize ORDER_CREATED and publish to Redis channel")
        void shouldSerializeAndPublishOrderCreated() throws Exception {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 42L, sampleOrderCreatedPayload());

            // When
            bridge.publish(event);

            // Then
            ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(channelCaptor.capture(), messageCaptor.capture());

            assertThat(channelCaptor.getValue()).isEqualTo("sse:restaurant-events");

            RedisEventMessage deserialized = objectMapper.readValue(messageCaptor.getValue(), RedisEventMessage.class);
            assertThat(deserialized.eventType()).isEqualTo("ORDER_CREATED");
            assertThat(deserialized.restaurantId()).isEqualTo(42L);
            assertThat(deserialized.sourceInstanceId()).isEqualTo("test-instance-1");
            assertThat(deserialized.payloadJson()).isNotBlank();

            // Verify payload round-trips correctly
            OrderCreatedPayload payload = objectMapper.readValue(deserialized.payloadJson(), OrderCreatedPayload.class);
            assertThat(payload.orderId()).isEqualTo(101L);
            assertThat(payload.totalAmount()).isEqualByComparingTo(new BigDecimal("29.50"));
            assertThat(payload.note()).isEqualTo("no onions");
        }

        @Test
        @DisplayName("Should publish to correct channel constant")
        void shouldPublishToCorrectChannel() {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.SERVICE_CALL, 1L, sampleServiceCallPayload());

            // When
            bridge.publish(event);

            // Then
            verify(redisTemplate).convertAndSend(eq("sse:restaurant-events"), anyString());
            assertThat(RedisEventBridge.CHANNEL).isEqualTo("sse:restaurant-events");
        }

        @Test
        @DisplayName("Should include instance ID in serialized message")
        void shouldIncludeInstanceIdInMessage() throws Exception {
            // Given
            bridge = new RedisEventBridge(redisTemplate, "backend-node-7", new SimpleMeterRegistry());
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_DONE, 5L, sampleOrderStatusPayload(OrderStatus.DONE));

            // When
            bridge.publish(event);

            // Then
            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(anyString(), captor.capture());
            RedisEventMessage msg = objectMapper.readValue(captor.getValue(), RedisEventMessage.class);
            assertThat(msg.sourceInstanceId()).isEqualTo("backend-node-7");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.restaurantqr.sse.bridge.RedisBridgeTest#allEventTypesWithPayloads")
        @DisplayName("Should serialize and publish all event types")
        void shouldPublishAllEventTypes(SseEventType type, Object payload) {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(type, 1L, payload);

            // When
            bridge.publish(event);

            // Then
            verify(redisTemplate).convertAndSend(eq("sse:restaurant-events"), anyString());
        }

        @Test
        @DisplayName("Should catch and not throw on serialization failure")
        void shouldNotThrowOnSerializationFailure() {
            // Given — un-serializable payload (circular reference)
            Object badPayload = new Object() {
                @SuppressWarnings("unused")
                public Object getSelf() { return this; }
            };
            SseOutboundEvent event = SseOutboundEvent.of(SseEventType.ORDER_CREATED, 1L, badPayload);

            // When / Then — no exception
            assertThatCode(() -> bridge.publish(event)).doesNotThrowAnyException();
            verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        }
    }

    // ==================== RedisEventSubscriber Tests ====================

    @Nested
    @DisplayName("RedisEventSubscriber — consume from Redis")
    class RedisEventSubscriberTests {

        @Mock
        private RestaurantEventPublisher eventPublisher;

        private RedisEventSubscriber subscriber;

        @BeforeEach
        void setUp() {
            subscriber = new RedisEventSubscriber(eventPublisher, "local-instance-A", new SimpleMeterRegistry());
        }

        private Message mockMessage(String json) {
            Message msg = mock(Message.class);
            when(msg.getBody()).thenReturn(json.getBytes());
            return msg;
        }

        private String buildRedisMessageJson(SseEventType type, Long restaurantId, Object payload) throws Exception {
            String payloadJson = objectMapper.writeValueAsString(payload);
            RedisEventMessage redisMsg = new RedisEventMessage(type.name(), restaurantId, "remote-instance-B", payloadJson);
            return objectMapper.writeValueAsString(redisMsg);
        }

        @Test
        @DisplayName("Should deserialize and dispatch ORDER_CREATED")
        void shouldDeserializeAndDispatchOrderCreated() throws Exception {
            // Given
            OrderCreatedPayload original = sampleOrderCreatedPayload();
            Message msg = mockMessage(buildRedisMessageJson(SseEventType.ORDER_CREATED, 42L, original));

            // When
            subscriber.onMessage(msg, null);

            // Then
            ArgumentCaptor<OrderCreatedPayload> captor = ArgumentCaptor.forClass(OrderCreatedPayload.class);
            verify(eventPublisher).publishOrderCreated(eq(42L), captor.capture());
            OrderCreatedPayload received = captor.getValue();
            assertThat(received.orderId()).isEqualTo(101L);
            assertThat(received.displayOrderNumber()).isEqualTo(5);
            assertThat(received.tableNumber()).isEqualTo(3);
            assertThat(received.totalAmount()).isEqualByComparingTo(new BigDecimal("29.50"));
            assertThat(received.note()).isEqualTo("no onions");
            assertThat(received.createdAt()).isEqualTo(Instant.parse("2026-03-27T10:15:30Z"));
        }

        @Test
        @DisplayName("Should deserialize and dispatch ORDER_SENT_TO_KITCHEN")
        void shouldDeserializeAndDispatchOrderSentToKitchen() throws Exception {
            // Given
            OrderStatusPayload original = sampleOrderStatusPayload(OrderStatus.SENT_TO_KITCHEN);
            Message msg = mockMessage(buildRedisMessageJson(SseEventType.ORDER_SENT_TO_KITCHEN, 10L, original));

            // When
            subscriber.onMessage(msg, null);

            // Then
            ArgumentCaptor<OrderStatusPayload> captor = ArgumentCaptor.forClass(OrderStatusPayload.class);
            verify(eventPublisher).publishOrderSentToKitchen(eq(10L), captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(OrderStatus.SENT_TO_KITCHEN);
            assertThat(captor.getValue().orderId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("Should deserialize and dispatch SERVICE_CALL")
        void shouldDeserializeAndDispatchServiceCall() throws Exception {
            // Given
            ServiceCallPayload original = sampleServiceCallPayload();
            Message msg = mockMessage(buildRedisMessageJson(SseEventType.SERVICE_CALL, 5L, original));

            // When
            subscriber.onMessage(msg, null);

            // Then
            ArgumentCaptor<ServiceCallPayload> captor = ArgumentCaptor.forClass(ServiceCallPayload.class);
            verify(eventPublisher).publishServiceCall(eq(5L), captor.capture());
            assertThat(captor.getValue().callId()).isEqualTo(201L);
            assertThat(captor.getValue().callType()).isEqualTo(CallType.WAITER);
            assertThat(captor.getValue().tableNumber()).isEqualTo(7);
        }

        @Test
        @DisplayName("Should deserialize and dispatch MENU_ITEM_UPDATED")
        void shouldDeserializeAndDispatchMenuItemUpdated() throws Exception {
            // Given
            MenuItemUpdatedPayload original = sampleMenuItemUpdatedPayload();
            Message msg = mockMessage(buildRedisMessageJson(SseEventType.MENU_ITEM_UPDATED, 8L, original));

            // When
            subscriber.onMessage(msg, null);

            // Then
            ArgumentCaptor<MenuItemUpdatedPayload> captor = ArgumentCaptor.forClass(MenuItemUpdatedPayload.class);
            verify(eventPublisher).publishMenuItemUpdated(eq(8L), captor.capture());
            assertThat(captor.getValue().itemId()).isEqualTo(301L);
            assertThat(captor.getValue().name()).isEqualTo("Hummus");
            assertThat(captor.getValue().price()).isEqualByComparingTo(new BigDecimal("5.99"));
            assertThat(captor.getValue().isAvailable()).isTrue();
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.restaurantqr.sse.bridge.RedisBridgeTest#allEventTypesWithPayloads")
        @DisplayName("Should dispatch all 7 event types to correct publisher method")
        void shouldDispatchAllSevenEventTypes(SseEventType type, Object payload) throws Exception {
            // Given
            Message msg = mockMessage(buildRedisMessageJson(type, 1L, payload));

            // When
            subscriber.onMessage(msg, null);

            // Then — verify correct method called based on type
            switch (type) {
                case ORDER_CREATED -> verify(eventPublisher).publishOrderCreated(eq(1L), any(OrderCreatedPayload.class));
                case ORDER_SENT_TO_KITCHEN -> verify(eventPublisher).publishOrderSentToKitchen(eq(1L), any(OrderStatusPayload.class));
                case ORDER_DONE -> verify(eventPublisher).publishOrderDone(eq(1L), any(OrderStatusPayload.class));
                case ORDER_CANCELLED -> verify(eventPublisher).publishOrderCancelled(eq(1L), any(OrderStatusPayload.class));
                case SERVICE_CALL -> verify(eventPublisher).publishServiceCall(eq(1L), any(ServiceCallPayload.class));
                case MENU_ITEM_UPDATED -> verify(eventPublisher).publishMenuItemUpdated(eq(1L), any(MenuItemUpdatedPayload.class));
                case MENU_ITEM_DELETED -> verify(eventPublisher).publishMenuItemDeleted(eq(1L), any(MenuItemDeletedPayload.class));
                default -> fail("Unexpected event type: " + type);
            }
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            // Given
            Message msg = mockMessage("{bad json!!!");

            // When / Then — no exception
            assertThatCode(() -> subscriber.onMessage(msg, null)).doesNotThrowAnyException();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should handle unknown event type gracefully (HEARTBEAT not in map)")
        void shouldHandleUnknownEventTypeGracefully() throws Exception {
            // Given — HEARTBEAT is a valid enum but not in PAYLOAD_TYPE_MAP
            String json = objectMapper.writeValueAsString(
                    new RedisEventMessage("HEARTBEAT", 1L, "other", "{}"));
            Message msg = mockMessage(json);

            // When / Then
            assertThatCode(() -> subscriber.onMessage(msg, null)).doesNotThrowAnyException();
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("Should handle invalid enum value gracefully")
        void shouldHandleInvalidEnumGracefully() throws Exception {
            // Given — NONEXISTENT is not a valid SseEventType
            String json = objectMapper.writeValueAsString(
                    new RedisEventMessage("NONEXISTENT", 1L, "other", "{}"));
            Message msg = mockMessage(json);

            // When / Then
            assertThatCode(() -> subscriber.onMessage(msg, null)).doesNotThrowAnyException();
            verifyNoInteractions(eventPublisher);
        }
    }

    // ==================== Serialization Round-Trip Tests ====================

    @Nested
    @DisplayName("Serialization round-trip — publish format → subscribe deserialize")
    class SerializationRoundTripTests {

        @Test
        @DisplayName("Should round-trip OrderCreatedPayload (BigDecimal, Instant)")
        void shouldRoundTripOrderCreatedPayload() throws Exception {
            // Given
            OrderCreatedPayload original = sampleOrderCreatedPayload();

            // Simulate publish path
            String payloadJson = objectMapper.writeValueAsString(original);
            RedisEventMessage redisMsg = new RedisEventMessage("ORDER_CREATED", 42L, "inst-1", payloadJson);
            String wireJson = objectMapper.writeValueAsString(redisMsg);

            // Simulate subscribe path
            RedisEventMessage received = objectMapper.readValue(wireJson, RedisEventMessage.class);
            OrderCreatedPayload deserialized = objectMapper.readValue(received.payloadJson(), OrderCreatedPayload.class);

            // Then
            assertThat(deserialized.orderId()).isEqualTo(original.orderId());
            assertThat(deserialized.displayOrderNumber()).isEqualTo(original.displayOrderNumber());
            assertThat(deserialized.tableNumber()).isEqualTo(original.tableNumber());
            assertThat(deserialized.totalAmount()).isEqualByComparingTo(original.totalAmount());
            assertThat(deserialized.note()).isEqualTo(original.note());
            assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
        }

        @Test
        @DisplayName("Should round-trip OrderStatusPayload (OrderStatus enum, nullable Instant)")
        void shouldRoundTripOrderStatusPayload() throws Exception {
            // Given
            OrderStatusPayload original = sampleOrderStatusPayload(OrderStatus.DONE);

            // Simulate publish → subscribe
            String payloadJson = objectMapper.writeValueAsString(original);
            RedisEventMessage redisMsg = new RedisEventMessage("ORDER_DONE", 1L, "inst-1", payloadJson);
            String wireJson = objectMapper.writeValueAsString(redisMsg);
            RedisEventMessage received = objectMapper.readValue(wireJson, RedisEventMessage.class);
            OrderStatusPayload deserialized = objectMapper.readValue(received.payloadJson(), OrderStatusPayload.class);

            // Then
            assertThat(deserialized.orderId()).isEqualTo(original.orderId());
            assertThat(deserialized.status()).isEqualTo(OrderStatus.DONE);
            assertThat(deserialized.sentToKitchenAt()).isEqualTo(original.sentToKitchenAt());
            assertThat(deserialized.doneAt()).isEqualTo(original.doneAt());
            assertThat(deserialized.cancelledAt()).isNull();
        }

        @Test
        @DisplayName("Should round-trip ServiceCallPayload (CallType enum)")
        void shouldRoundTripServiceCallPayload() throws Exception {
            // Given
            ServiceCallPayload original = sampleServiceCallPayload();

            // Simulate publish → subscribe
            String payloadJson = objectMapper.writeValueAsString(original);
            RedisEventMessage redisMsg = new RedisEventMessage("SERVICE_CALL", 1L, "inst-1", payloadJson);
            String wireJson = objectMapper.writeValueAsString(redisMsg);
            RedisEventMessage received = objectMapper.readValue(wireJson, RedisEventMessage.class);
            ServiceCallPayload deserialized = objectMapper.readValue(received.payloadJson(), ServiceCallPayload.class);

            // Then
            assertThat(deserialized.callId()).isEqualTo(original.callId());
            assertThat(deserialized.tableNumber()).isEqualTo(original.tableNumber());
            assertThat(deserialized.callType()).isEqualTo(CallType.WAITER);
            assertThat(deserialized.createdAt()).isEqualTo(original.createdAt());
        }

        @Test
        @DisplayName("Should round-trip MenuItemUpdatedPayload (boolean, nullable Long)")
        void shouldRoundTripMenuItemUpdatedPayload() throws Exception {
            // Given
            MenuItemUpdatedPayload original = sampleMenuItemUpdatedPayload();

            // Simulate publish → subscribe
            String payloadJson = objectMapper.writeValueAsString(original);
            RedisEventMessage redisMsg = new RedisEventMessage("MENU_ITEM_UPDATED", 1L, "inst-1", payloadJson);
            String wireJson = objectMapper.writeValueAsString(redisMsg);
            RedisEventMessage received = objectMapper.readValue(wireJson, RedisEventMessage.class);
            MenuItemUpdatedPayload deserialized = objectMapper.readValue(received.payloadJson(), MenuItemUpdatedPayload.class);

            // Then
            assertThat(deserialized.itemId()).isEqualTo(original.itemId());
            assertThat(deserialized.categoryId()).isEqualTo(original.categoryId());
            assertThat(deserialized.name()).isEqualTo(original.name());
            assertThat(deserialized.price()).isEqualByComparingTo(original.price());
            assertThat(deserialized.isAvailable()).isEqualTo(original.isAvailable());
            assertThat(deserialized.isActive()).isEqualTo(original.isActive());
            assertThat(deserialized.updatedAt()).isEqualTo(original.updatedAt());
            assertThat(deserialized.displayOrder()).isEqualTo(original.displayOrder());
        }
    }

    // ==================== LocalEventBridge Tests ====================

    @Nested
    @DisplayName("LocalEventBridge — fallback direct dispatch")
    class LocalEventBridgeTests {

        @Mock
        private RestaurantEventPublisher eventPublisher;

        private LocalEventBridge bridge;

        @BeforeEach
        void setUp() {
            bridge = new LocalEventBridge(eventPublisher);
        }

        @Test
        @DisplayName("Should delegate ORDER_CREATED to publisher")
        void shouldDelegateOrderCreatedToPublisher() {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 42L, sampleOrderCreatedPayload());

            // When
            bridge.publish(event);

            // Then
            verify(eventPublisher).publishOrderCreated(eq(42L), any(OrderCreatedPayload.class));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.restaurantqr.sse.bridge.RedisBridgeTest#allEventTypesWithPayloads")
        @DisplayName("Should delegate all 7 event types to correct publisher method")
        void shouldDelegateAllSevenEventTypes(SseEventType type, Object payload) {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(type, 1L, payload);

            // When
            bridge.publish(event);

            // Then
            switch (type) {
                case ORDER_CREATED -> verify(eventPublisher).publishOrderCreated(eq(1L), any(OrderCreatedPayload.class));
                case ORDER_SENT_TO_KITCHEN -> verify(eventPublisher).publishOrderSentToKitchen(eq(1L), any(OrderStatusPayload.class));
                case ORDER_DONE -> verify(eventPublisher).publishOrderDone(eq(1L), any(OrderStatusPayload.class));
                case ORDER_CANCELLED -> verify(eventPublisher).publishOrderCancelled(eq(1L), any(OrderStatusPayload.class));
                case SERVICE_CALL -> verify(eventPublisher).publishServiceCall(eq(1L), any(ServiceCallPayload.class));
                case MENU_ITEM_UPDATED -> verify(eventPublisher).publishMenuItemUpdated(eq(1L), any(MenuItemUpdatedPayload.class));
                case MENU_ITEM_DELETED -> verify(eventPublisher).publishMenuItemDeleted(eq(1L), any(MenuItemDeletedPayload.class));
                default -> fail("Unexpected event type: " + type);
            }
        }

        @Test
        @DisplayName("Should catch and not throw on publisher exception")
        void shouldCatchExceptionFromPublisher() {
            // Given
            doThrow(new RuntimeException("broadcast failure"))
                    .when(eventPublisher).publishOrderCreated(anyLong(), any());
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 1L, sampleOrderCreatedPayload());

            // When / Then
            assertThatCode(() -> bridge.publish(event)).doesNotThrowAnyException();
        }
    }

    // ==================== SseAfterCommitListener Tests ====================

    @Nested
    @DisplayName("SseAfterCommitListener — bridge delegation")
    class SseAfterCommitListenerTests {

        @Mock
        private SseEventBridge eventBridge;

        private SseAfterCommitListener listener;

        @BeforeEach
        void setUp() {
            listener = new SseAfterCommitListener(eventBridge);
        }

        @Test
        @DisplayName("Should delegate event to bridge")
        void shouldDelegateToBridge() {
            // Given
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 42L, sampleOrderCreatedPayload());

            // When
            listener.onSseOutboundEvent(event);

            // Then
            verify(eventBridge).publish(event);
        }

        @Test
        @DisplayName("Should swallow exception from bridge")
        void shouldSwallowBridgeException() {
            // Given
            doThrow(new RuntimeException("Redis down"))
                    .when(eventBridge).publish(any());
            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.SERVICE_CALL, 1L, sampleServiceCallPayload());

            // When / Then
            assertThatCode(() -> listener.onSseOutboundEvent(event)).doesNotThrowAnyException();
        }
    }
}




