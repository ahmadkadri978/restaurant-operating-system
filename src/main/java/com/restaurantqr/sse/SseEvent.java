package com.restaurantqr.sse;

import java.time.Instant;

/**
 * Generic SSE event wrapper containing type, restaurant scope, and payload.
 *
 * @param <T> the payload type
 */
public record SseEvent<T>(
        SseEventType type,
        Long restaurantId,
        Instant timestamp,
        T payload
) {
    public static <T> SseEvent<T> of(SseEventType type, Long restaurantId, T payload) {
        return new SseEvent<>(type, restaurantId, Instant.now(), payload);
    }
    
    public static SseEvent<Void> heartbeat(Long restaurantId) {
        return new SseEvent<>(SseEventType.HEARTBEAT, restaurantId, Instant.now(), null);
    }
    
    public static SseEvent<String> connected(Long restaurantId) {
        return new SseEvent<>(SseEventType.CONNECTED, restaurantId, Instant.now(), "Connected to SSE stream");
    }
}

