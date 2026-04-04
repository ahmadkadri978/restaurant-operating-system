package com.restaurantqr.sse.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurantqr.sse.SseOutboundEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes SSE events to the Redis pub/sub channel {@code sse:restaurant-events}
 * so that all backend instances receive the event and broadcast to their local emitters.
 * <p>
 * Activated only when {@code app.redis.enabled=true}.
 * When absent, {@link LocalEventBridge} handles events as a direct local fallback.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisEventBridge implements SseEventBridge {

    private static final Logger log = LoggerFactory.getLogger(RedisEventBridge.class);

    static final String CHANNEL = "sse:restaurant-events";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String instanceId;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public RedisEventBridge(StringRedisTemplate redisTemplate,
                            @Value("${app.instance-id:unknown}") String instanceId,
                            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.instanceId = instanceId;

        // Phase C: Redis publish counters
        this.publishSuccessCounter = Counter.builder("sse.redis.publish")
                .tag("result", "success")
                .description("Redis SSE event publish count")
                .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("sse.redis.publish")
                .tag("result", "failure")
                .description("Redis SSE event publish failure count")
                .register(meterRegistry);

        log.info("RedisEventBridge activated -- SSE events will fan-out via Redis channel '{}'", CHANNEL);
    }

    @Override
    public void publish(SseOutboundEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event.payload());

            RedisEventMessage message = new RedisEventMessage(
                    event.eventType().name(),
                    event.restaurantId(),
                    instanceId,
                    payloadJson
            );

            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL, messageJson);

            publishSuccessCounter.increment();
            log.info("[Redis PUBLISH] type={}, restaurantId={}, instanceId={}",
                    event.eventType(), event.restaurantId(), instanceId);
        } catch (Exception e) {
            publishFailureCounter.increment();
            log.error("[Redis PUBLISH FAILED] type={}, restaurantId={}, instanceId={}",
                    event.eventType(), event.restaurantId(), instanceId, e);
        }
    }
}

