package com.restaurantqr.sse;

/**
 * Internal domain event published via Spring's {@code ApplicationEventPublisher}
 * inside transactional service methods. The {@link SseAfterCommitListener} consumes
 * these events only after the enclosing transaction commits, preventing phantom
 * SSE broadcasts on rollback.
 *
 * @param eventType    the SSE event type
 * @param restaurantId the restaurant scope
 * @param payload      the typed payload (e.g. {@code OrderCreatedPayload})
 */
public record SseOutboundEvent(
        SseEventType eventType,
        Long restaurantId,
        Object payload
) {

    public static SseOutboundEvent of(SseEventType eventType, Long restaurantId, Object payload) {
        return new SseOutboundEvent(eventType, restaurantId, payload);
    }
}

