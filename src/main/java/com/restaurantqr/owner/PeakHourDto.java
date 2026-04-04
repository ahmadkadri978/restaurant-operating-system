package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "PeakHourDto", description = "Peak hour statistics (hour of day with most orders)")
public record PeakHourDto(
        @Schema(description = "Hour of day (0-23)", example = "13")
        Integer hour,

        @Schema(description = "Number of orders in this hour", example = "35")
        Long orderCount,

        @Schema(description = "Revenue in this hour", example = "425.50")
        BigDecimal revenue
) {}

