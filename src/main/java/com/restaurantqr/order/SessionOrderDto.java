package com.restaurantqr.order;

import com.restaurantqr.order.staff.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Order summary visible to the customer within the current session")
public record SessionOrderDto(
        @Schema(description = "Internal order ID", example = "101") Long orderId,
        @Schema(description = "Restaurant-specific display order number", example = "15") Integer displayOrderNumber,
        @Schema(description = "Current order status") OrderStatus status,
        @Schema(example = "2026-03-18T12:30:00Z") Instant createdAt,
        @Schema(example = "25.50") BigDecimal totalAmount,
        @Schema(description = "Optional order note") String note,
        @Schema(description = "Ordered items") List<SessionOrderItemDto> items
) {}

