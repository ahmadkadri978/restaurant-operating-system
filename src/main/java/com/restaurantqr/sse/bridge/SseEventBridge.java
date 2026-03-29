package com.restaurantqr.sse.bridge;

import com.restaurantqr.sse.SseOutboundEvent;

/**
 * Strategy interface for SSE event fan-out after transaction commit.
 * <p>
 * When Redis is available, {@link RedisEventBridge} publishes to a Redis pub/sub
 * channel so all backend instances receive the event. When Redis is absent,
 * {@link LocalEventBridge} delegates directly to the local SSE registries.
 */
public interface SseEventBridge {

    /**
     * Publish an outbound SSE event for fan-out.
     *
     * @param event the committed event to broadcast
     */
    void publish(SseOutboundEvent event);
}

