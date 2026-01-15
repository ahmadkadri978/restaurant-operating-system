package com.restaurantqr.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Order creation response")
public record SubmitOrderResponse(
        @Schema(example = "101") Long orderId,
        @Schema(example = "2026-01-07T21:15:30.123Z") Instant createdAt,
        @Schema(example = "12.50") BigDecimal totalAmount
) {}