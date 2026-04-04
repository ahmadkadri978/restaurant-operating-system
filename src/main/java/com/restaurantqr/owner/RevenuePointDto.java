package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "RevenuePointDto", description = "Revenue data point for trend chart")
public record RevenuePointDto(
        @Schema(description = "Timestamp of the data point", example = "2026-03-12T10:00:00Z")
        Instant timestamp,

        @Schema(description = "Revenue amount at this point", example = "234.50")
        BigDecimal revenue,

        @Schema(description = "Number of orders at this point", example = "12")
        Long orderCount
) {}

