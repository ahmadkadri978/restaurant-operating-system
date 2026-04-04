package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A single order item line in a bill preview")
public record BillItemDto(
        @Schema(description = "Item name", example = "شاورما دجاج")
        String itemName,

        @Schema(description = "Aggregated quantity", example = "2")
        int quantity,

        @Schema(description = "Unit price snapshot", example = "5000.00")
        BigDecimal unitPrice,

        @Schema(description = "Line total = quantity × unitPrice", example = "10000.00")
        BigDecimal lineTotal
) {}

