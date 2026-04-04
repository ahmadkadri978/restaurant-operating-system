package com.restaurantqr.table;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request body for closing a session with payment")
public record CloseWithPaymentRequest(
        @NotNull
        @DecimalMin(value = "0.00", message = "Amount received must be >= 0")
        @Schema(description = "Amount received from the customer", example = "30000.00")
        BigDecimal amountReceived,

        @NotNull
        @DecimalMin(value = "0.00", message = "Tip amount must be >= 0")
        @Schema(description = "Tip (gratuity) left by the customer", example = "2000.00")
        BigDecimal tipAmount
) {}


