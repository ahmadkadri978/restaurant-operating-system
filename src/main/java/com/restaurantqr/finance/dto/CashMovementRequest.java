package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to record a cash movement (cash-in or cash-out)")
public record CashMovementRequest(
        @Schema(description = "Movement type: CASH_IN or CASH_OUT", example = "CASH_OUT")
        String type,

        @Schema(description = "Amount of the movement", example = "5000.00")
        BigDecimal amount,

        @Schema(description = "Reason for the movement", example = "شراء مواد تنظيف")
        String reason
) {}

