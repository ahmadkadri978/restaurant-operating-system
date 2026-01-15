package com.restaurantqr.order.staff;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Order status update response")
public record OrderStatusResponse(
        @Schema(example = "101") Long orderId,
        @Schema(example = "SENT_TO_KITCHEN") OrderStatus status,
        @Schema(example = "2026-01-07T21:30:00.000Z") Instant sentToKitchenAt
) {}