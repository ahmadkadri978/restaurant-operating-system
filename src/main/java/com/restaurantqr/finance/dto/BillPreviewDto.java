package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Rich bill preview with order items and charges for an open session")
public record BillPreviewDto(
        @Schema(description = "Table number", example = "5")
        int tableNumber,

        @Schema(description = "Session code", example = "4821")
        String sessionCode,

        @Schema(description = "Session opened at")
        Instant sessionOpenedAt,

        @Schema(description = "Sum of all ordered items (excl. cancelled)", example = "20000.00")
        BigDecimal itemsSubtotal,

        @Schema(description = "Sum of all applied charges", example = "5000.00")
        BigDecimal chargesTotal,

        @Schema(description = "Final total = items + charges", example = "25000.00")
        BigDecimal grandTotal,

        @Schema(description = "Currency code", example = "SYP")
        String currency,

        @Schema(description = "Aggregated order item lines")
        List<BillItemDto> items,

        @Schema(description = "Itemized charges breakdown")
        List<BillChargeDto> charges
) {}

