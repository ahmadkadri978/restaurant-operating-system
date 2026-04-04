package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Request to open a cash register session")
public record OpenCashRegisterRequest(
        @Schema(description = "Cash physically counted in the drawer at shift start", example = "50000.00")
        BigDecimal openingBalance
) {}

