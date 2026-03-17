package com.restaurantqr.sse;

import com.restaurantqr.order.staff.OrderStatus;

import java.time.Instant;

/**
 * Payload for order status change events (SENT_TO_KITCHEN, DONE, CANCELLED).
 */
public record OrderStatusPayload(
        Long orderId,
        int tableNumber,
        OrderStatus status,
        Instant sentToKitchenAt,
        Instant doneAt,
        Instant cancelledAt
) {}

