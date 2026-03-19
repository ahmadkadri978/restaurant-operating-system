package com.restaurantqr.sse;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload for ORDER_CREATED events.
 */
public record OrderCreatedPayload(
        Long orderId,
        Integer displayOrderNumber,
        int tableNumber,
        BigDecimal totalAmount,
        String note,
        Instant createdAt
) {}

