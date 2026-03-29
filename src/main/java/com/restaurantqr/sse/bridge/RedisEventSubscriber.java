package com.restaurantqr.sse.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantqr.sse.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the Redis pub/sub channel {@code sse:restaurant-events} and
 * delegates incoming events to {@link RestaurantEventPublisher} for local
 * SSE broadcast on this instance.
 * <p>
 * Every backend instance receives every event (uniform fan-out).
 * Each instance broadcasts only to its own locally-connected emitters.
 */
@Component
public class RedisEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventSubscriber.class);

    private final RestaurantEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    private final Counter consumeSuccessCounter;
    private final Counter consumeFailureCounter;

    public RedisEventSubscriber(RestaurantEventPublisher eventPublisher,
                                @Value("${app.instance-id:unknown}") String instanceId,
                                MeterRegistry meterRegistry) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.instanceId = instanceId;

        // Phase C: Redis consume counters
        this.consumeSuccessCounter = Counter.builder("sse.redis.consume")
                .tag("result", "success")
                .description("Redis SSE event consume count")
                .register(meterRegistry);
        this.consumeFailureCounter = Counter.builder("sse.redis.consume")
                .tag("result", "failure")
                .description("Redis SSE event consume failure count")
                .register(meterRegistry);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            RedisEventMessage redisMsg = objectMapper.readValue(json, RedisEventMessage.class);

            SseEventType eventType = SseEventType.valueOf(redisMsg.eventType());
            Class<?> payloadClass = RedisEventMessage.PAYLOAD_TYPE_MAP.get(eventType);
            if (payloadClass == null) {
                log.warn("[Redis CONSUME] Unknown event type: {}", redisMsg.eventType());
                consumeFailureCounter.increment();
                return;
            }

            Object payload = objectMapper.readValue(redisMsg.payloadJson(), payloadClass);

            log.info("[Redis CONSUME] type={}, restaurantId={}, fromInstance={}, localInstance={}",
                    eventType, redisMsg.restaurantId(), redisMsg.sourceInstanceId(), instanceId);

            dispatchToLocalRegistry(eventType, redisMsg.restaurantId(), payload);
            consumeSuccessCounter.increment();

        } catch (Exception e) {
            consumeFailureCounter.increment();
            log.error("[Redis CONSUME FAILED] Could not process Redis message. instanceId={}", instanceId, e);
        }
    }

    private void dispatchToLocalRegistry(SseEventType eventType, Long restaurantId, Object payload) {
        switch (eventType) {
            case ORDER_CREATED ->
                eventPublisher.publishOrderCreated(restaurantId, (OrderCreatedPayload) payload);
            case ORDER_SENT_TO_KITCHEN ->
                eventPublisher.publishOrderSentToKitchen(restaurantId, (OrderStatusPayload) payload);
            case ORDER_DONE ->
                eventPublisher.publishOrderDone(restaurantId, (OrderStatusPayload) payload);
            case ORDER_CANCELLED ->
                eventPublisher.publishOrderCancelled(restaurantId, (OrderStatusPayload) payload);
            case SERVICE_CALL ->
                eventPublisher.publishServiceCall(restaurantId, (ServiceCallPayload) payload);
            case MENU_ITEM_UPDATED ->
                eventPublisher.publishMenuItemUpdated(restaurantId, (MenuItemUpdatedPayload) payload);
            case MENU_ITEM_DELETED ->
                eventPublisher.publishMenuItemDeleted(restaurantId, (MenuItemDeletedPayload) payload);
            default ->
                log.warn("[Redis CONSUME] Unhandled event type for local dispatch: {}", eventType);
        }
    }
}

