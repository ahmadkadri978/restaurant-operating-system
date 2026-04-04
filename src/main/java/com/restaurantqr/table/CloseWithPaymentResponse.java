package com.restaurantqr.table;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Response after closing a session with payment")
public record CloseWithPaymentResponse(
        @Schema(description = "Table ID")
        Long tableId,

        @Schema(description = "Whether the session was closed")
        boolean closed,

        @Schema(description = "Generated invoice ID")
        Long invoiceId,

        @Schema(description = "Generated invoice number", example = "INV-20260330-003")
        String invoiceNumber,

        @Schema(description = "Bill grand total", example = "25000.00")
        BigDecimal grandTotal,

        @Schema(description = "Amount received from customer", example = "30000.00")
        BigDecimal amountReceived,

        @Schema(description = "Tip (gratuity) left by customer", example = "2000.00")
        BigDecimal tipAmount,

        @Schema(description = "Change returned to customer", example = "3000.00")
        BigDecimal amountReturned
) {}


