package com.restaurantqr.sse.bridge;

import com.restaurantqr.sse.*;

import java.util.Map;

/**
 * Serializable envelope for SSE events transmitted over Redis pub/sub.
 * <p>
 * The {@code payloadJson} is the Jackson-serialized payload string.
 * The subscriber uses {@link #PAYLOAD_TYPE_MAP} to resolve the concrete
 * payload class from the {@code eventType} — no class names are sent over the wire.
 *
 * @param eventType        the SSE event type name (e.g. "ORDER_CREATED")
 * @param restaurantId     the restaurant scope
 * @param sourceInstanceId the instance that originated the event
 * @param payloadJson      the Jackson-serialized payload
 */
public record RedisEventMessage(
        String eventType,
        Long restaurantId,
        String sourceInstanceId,
        String payloadJson
) {

    /** Maps each event type to its concrete payload class for safe deserialization. */
    public static final Map<SseEventType, Class<?>> PAYLOAD_TYPE_MAP = Map.of(
            SseEventType.ORDER_CREATED, OrderCreatedPayload.class,
            SseEventType.ORDER_SENT_TO_KITCHEN, OrderStatusPayload.class,
            SseEventType.ORDER_DONE, OrderStatusPayload.class,
            SseEventType.ORDER_CANCELLED, OrderStatusPayload.class,
            SseEventType.SERVICE_CALL, ServiceCallPayload.class,
            SseEventType.MENU_ITEM_UPDATED, MenuItemUpdatedPayload.class,
            SseEventType.MENU_ITEM_DELETED, MenuItemDeletedPayload.class
    );
}

