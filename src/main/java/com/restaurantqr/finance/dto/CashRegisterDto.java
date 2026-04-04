package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Cash register session details with running totals")
public record CashRegisterDto(
        Long id,
        String status,
        Instant openedAt,
        Instant closedAt,
        String openedByUsername,
        String closedByUsername,
        BigDecimal openingBalance,
        BigDecimal totalReceived,
        BigDecimal totalTips,
        BigDecimal totalReturned,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        BigDecimal expectedClosing,
        BigDecimal actualClosing,
        BigDecimal discrepancy,
        String discrepancyType,
        long paymentCount,
        String notes,
        List<CashMovementDto> movements
) {}

