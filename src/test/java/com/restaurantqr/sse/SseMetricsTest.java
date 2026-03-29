package com.restaurantqr.sse;

import com.restaurantqr.sse.bridge.RedisEventBridge;
import com.restaurantqr.sse.bridge.RedisEventSubscriber;
import com.restaurantqr.sse.bridge.RedisEventMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase C: Unit tests for Micrometer metrics in SSE registries and Redis bridge.
 */
@DisplayName("SSE Metrics Tests (Phase C)")
class SseMetricsTest {

    private static OrderCreatedPayload samplePayload() {
        return new OrderCreatedPayload(101L, 5, 3, new BigDecimal("29.50"), "no onions",
                Instant.parse("2026-03-27T10:15:30Z"));
    }

    // ==================== SseEmitterRegistry Gauge ====================

    @Nested
    @DisplayName("SseEmitterRegistry -- staff connection gauge")
    class StaffConnectionGaugeTests {

        private MeterRegistry meterRegistry;
        private SseEmitterRegistry registry;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            registry = new SseEmitterRegistry(meterRegistry);
        }

        @Test
        @DisplayName("Should register gauge with initial value 0")
        void shouldRegisterGaugeWithZero() {
            Gauge gauge = meterRegistry.find("sse.staff.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should reflect connection count in gauge after register")
        void shouldReflectConnectionCount() {
            SseEmitter emitter1 = new SseEmitter();
            SseEmitter emitter2 = new SseEmitter();
            registry.register(1L, emitter1);
            registry.register(1L, emitter2);

            Gauge gauge = meterRegistry.find("sse.staff.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("Should decrease gauge after emitter removal")
        void shouldDecreaseGaugeAfterRemoval() {
            SseEmitter emitter = new SseEmitter();
            registry.register(1L, emitter);
            registry.remove(1L, emitter);

            Gauge gauge = meterRegistry.find("sse.staff.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should track connections across multiple restaurants")
        void shouldTrackAcrossRestaurants() {
            registry.register(1L, new SseEmitter());
            registry.register(2L, new SseEmitter());
            registry.register(2L, new SseEmitter());

            Gauge gauge = meterRegistry.find("sse.staff.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(3.0);
        }
    }

    // ==================== PublicMenuSseRegistry Gauge ====================

    @Nested
    @DisplayName("PublicMenuSseRegistry -- public connection gauge")
    class PublicConnectionGaugeTests {

        private MeterRegistry meterRegistry;
        private PublicMenuSseRegistry registry;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            registry = new PublicMenuSseRegistry(meterRegistry);
        }

        @Test
        @DisplayName("Should register gauge with initial value 0")
        void shouldRegisterGaugeWithZero() {
            Gauge gauge = meterRegistry.find("sse.public.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Should reflect connection count in gauge")
        void shouldReflectConnectionCount() {
            registry.register(1L, new SseEmitter());
            registry.register(1L, new SseEmitter());

            Gauge gauge = meterRegistry.find("sse.public.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(2.0);
        }

        @Test
        @DisplayName("Should decrease gauge after removal")
        void shouldDecreaseAfterRemoval() {
            SseEmitter emitter = new SseEmitter();
            registry.register(1L, emitter);
            registry.remove(1L, emitter);

            Gauge gauge = meterRegistry.find("sse.public.connections.active").gauge();
            assertThat(gauge).isNotNull();
            assertThat(gauge.value()).isEqualTo(0.0);
        }
    }

    // ==================== RedisEventBridge Counters ====================

    @Nested
    @DisplayName("RedisEventBridge -- publish counters")
    class RedisPublishCounterTests {

        @Test
        @DisplayName("Should increment success counter on successful publish")
        void shouldIncrementSuccessCounter() {
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            RedisEventBridge bridge = new RedisEventBridge(redisTemplate, "test-1", meterRegistry);

            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 1L, samplePayload());

            bridge.publish(event);

            Counter successCounter = meterRegistry.find("sse.redis.publish")
                    .tag("result", "success").counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment failure counter on exception")
        void shouldIncrementFailureCounter() {
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            doThrow(new RuntimeException("Redis down")).when(redisTemplate)
                    .convertAndSend(anyString(), anyString());
            RedisEventBridge bridge = new RedisEventBridge(redisTemplate, "test-1", meterRegistry);

            SseOutboundEvent event = SseOutboundEvent.of(
                    SseEventType.ORDER_CREATED, 1L, samplePayload());

            bridge.publish(event);

            Counter failureCounter = meterRegistry.find("sse.redis.publish")
                    .tag("result", "failure").counter();
            assertThat(failureCounter).isNotNull();
            assertThat(failureCounter.count()).isEqualTo(1.0);
        }
    }

    // ==================== RedisEventSubscriber Counters ====================

    @Nested
    @DisplayName("RedisEventSubscriber -- consume counters")
    class RedisConsumeCounterTests {

        private static final ObjectMapper objectMapper = createObjectMapper();

        private static ObjectMapper createObjectMapper() {
            ObjectMapper om = new ObjectMapper();
            om.registerModule(new JavaTimeModule());
            om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return om;
        }

        @Test
        @DisplayName("Should increment success counter on successful consume")
        void shouldIncrementSuccessCounter() throws Exception {
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            RestaurantEventPublisher publisher = mock(RestaurantEventPublisher.class);
            RedisEventSubscriber subscriber = new RedisEventSubscriber(publisher, "test-1", meterRegistry);

            String payloadJson = objectMapper.writeValueAsString(samplePayload());
            RedisEventMessage msg = new RedisEventMessage(
                    "ORDER_CREATED", 1L, "remote-instance", payloadJson);
            String msgJson = objectMapper.writeValueAsString(msg);

            Message redisMessage = mock(Message.class);
            when(redisMessage.getBody()).thenReturn(msgJson.getBytes());

            subscriber.onMessage(redisMessage, null);

            Counter successCounter = meterRegistry.find("sse.redis.consume")
                    .tag("result", "success").counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("Should increment failure counter on malformed message")
        void shouldIncrementFailureCounterOnBadMessage() {
            MeterRegistry meterRegistry = new SimpleMeterRegistry();
            RestaurantEventPublisher publisher = mock(RestaurantEventPublisher.class);
            RedisEventSubscriber subscriber = new RedisEventSubscriber(publisher, "test-1", meterRegistry);

            Message redisMessage = mock(Message.class);
            when(redisMessage.getBody()).thenReturn("not valid json".getBytes());

            subscriber.onMessage(redisMessage, null);

            Counter failureCounter = meterRegistry.find("sse.redis.consume")
                    .tag("result", "failure").counter();
            assertThat(failureCounter).isNotNull();
            assertThat(failureCounter.count()).isEqualTo(1.0);
        }
    }
}


