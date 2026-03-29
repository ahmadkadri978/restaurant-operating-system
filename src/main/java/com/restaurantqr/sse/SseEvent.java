package com.restaurantqr.sse;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic SSE event envelope containing type, restaurant scope, identity, and payload.
 * <p>
 * The {@code eventId} is used as the SSE {@code id} field so the browser populates
 * {@code Last-Event-ID} on reconnect (replay is a Phase B concern).
 *
 * @param <T> the payload type
 */
public record SseEvent<T>(
        String eventId,
        SseEventType type,
        Long restaurantId,
        Instant timestamp,
        int payloadVersion,
        String sourceInstanceId,
        T payload
) {

    /** Business event with identity and instance origin. */
    public static <T> SseEvent<T> of(SseEventType type, Long restaurantId, T payload, String sourceInstanceId) {
        return new SseEvent<>(
                UUID.randomUUID().toString(),
                type,
                restaurantId,
                Instant.now(),
                1,
                sourceInstanceId,
                payload
        );
    }

    /** Convenience overload without explicit instance id (used by heartbeat / connected). */
    public static <T> SseEvent<T> of(SseEventType type, Long restaurantId, T payload) {
        return of(type, restaurantId, payload, null);
    }

    public static SseEvent<Void> heartbeat(Long restaurantId) {
        return new SseEvent<>(
                UUID.randomUUID().toString(),
                SseEventType.HEARTBEAT,
                restaurantId,
                Instant.now(),
                1,
                null,
                null
        );
    }

    public static SseEvent<String> connected(Long restaurantId) {
        return new SseEvent<>(
                UUID.randomUUID().toString(),
                SseEventType.CONNECTED,
                restaurantId,
                Instant.now(),
                1,
                null,
                "Connected to SSE stream"
        );
    }
}
