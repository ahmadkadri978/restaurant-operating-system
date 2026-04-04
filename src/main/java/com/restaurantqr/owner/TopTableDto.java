package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "TopTableDto", description = "Top table statistics by orders and revenue")
public record TopTableDto(
        @Schema(description = "Table ID", example = "5")
        Long tableId,

        @Schema(description = "Table number", example = "12")
        Integer tableNumber,

        @Schema(description = "Number of orders from this table", example = "25")
        Long orderCount,

        @Schema(description = "Total revenue from this table", example = "320.75")
        BigDecimal totalRevenue
) {}

