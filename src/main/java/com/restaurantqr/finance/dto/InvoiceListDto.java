package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Invoice summary for list/search results")
public record InvoiceListDto(
        @Schema(description = "Invoice ID (used for PDF download)", example = "42")
        Long invoiceId,

        @Schema(description = "Invoice number", example = "INV-20260329-003")
        String invoiceNumber,

        @Schema(description = "Table number", example = "5")
        int tableNumber,

        @Schema(description = "Session code", example = "2323")
        String sessionCode,

        @Schema(description = "Session opened at")
        Instant sessionOpenedAt,

        @Schema(description = "Session closed at")
        Instant sessionClosedAt,

        @Schema(description = "Items subtotal (food/drink only)", example = "15000.00")
        BigDecimal itemsSubtotal,

        @Schema(description = "Charges total (taxes/fees)", example = "2850.00")
        BigDecimal chargesTotal,

        @Schema(description = "Final total including all charges (items + charges)", example = "17850.00")
        BigDecimal grandTotal,

        @Schema(description = "Tip/gratuity amount (0 if none)", example = "2000.00")
        BigDecimal tipAmount,

        @Schema(description = "Currency code", example = "SYP")
        String currency,

        @Schema(description = "Invoice creation timestamp")
        Instant createdAt
) {}

