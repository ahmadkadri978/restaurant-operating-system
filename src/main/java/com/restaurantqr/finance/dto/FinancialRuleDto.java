package com.restaurantqr.finance.dto;

import com.restaurantqr.finance.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Financial rule configured for a restaurant")
public record FinancialRuleDto(
        @Schema(description = "Rule ID", example = "1")
        Long id,

        @Schema(description = "Arabic display name", example = "ضريبة القيمة المضافة")
        String nameAr,

        @Schema(description = "English display name", example = "VAT")
        String nameEn,

        @Schema(description = "Rule type: FIXED or PERCENTAGE", example = "PERCENTAGE")
        RuleType ruleType,

        @Schema(description = "Value: fixed amount or percentage rate", example = "10.0000")
        BigDecimal value,

        @Schema(description = "Application order (lower = applied first)", example = "1")
        Integer applyOrder,

        @Schema(description = "Whether percentage is compound (applied on subtotal + prior charges)")
        boolean isCompound,

        @Schema(description = "Whether the rule is currently active")
        boolean isActive,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {}

