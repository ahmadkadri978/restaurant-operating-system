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

        @Schema(description = "Final total including all charges", example = "17850.00")
        BigDecimal grandTotal,

        @Schema(description = "Currency code", example = "SYP")
        String currency,

        @Schema(description = "Invoice creation timestamp")
        Instant createdAt
) {}

