package com.restaurantqr.order.staff;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(name = "OrderDetailsDto", description = "Full order details including items")
public record OrderDetailsDto(
        @Schema(description = "Order ID", example = "101")
        Long orderId,

        @Schema(description = "Table number", example = "5")
        Integer tableNumber,

        @Schema(description = "Order creation timestamp", example = "2026-01-07T21:15:30.123Z")
        Instant createdAt,

        @Schema(description = "Order total amount", example = "12.50")
        BigDecimal totalAmount,

        @Schema(description = "Optional order note", example = "Please bring napkins")
        String note,

        @Schema(description = "List of ordered items")
        List<OrderItemDto> items,
        @Schema(description = "Order status", example = "NEW/SENT_TO_KITCHEN")
        OrderStatus status

) {}

