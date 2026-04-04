package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Cash movement record")
public record CashMovementDto(
        Long id,
        String type,
        BigDecimal amount,
        String reason,
        String recordedByUsername,
        Instant createdAt
) {}

