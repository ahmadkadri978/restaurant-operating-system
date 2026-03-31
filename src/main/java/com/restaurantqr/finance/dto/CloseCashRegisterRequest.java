package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to close a cash register session")
public record CloseCashRegisterRequest(
        @Schema(description = "Cash physically counted by manager at end of shift", example = "148500.00")
        BigDecimal actualClosing,

        @Schema(description = "Optional notes for the close-out", example = "عدّ الكاش تم بواسطة أحمد")
        String notes
) {}

