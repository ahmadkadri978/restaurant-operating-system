package com.restaurantqr.owner;

import com.restaurantqr.finance.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Revenue breakdown by charge type")
public record ChargeBreakdownDto(
        @Schema(description = "Charge name (Arabic)", example = "ضريبة القيمة المضافة")
        String chargeName,

        @Schema(description = "Rule type", example = "PERCENTAGE")
        RuleType ruleType,

        @Schema(description = "Total revenue collected from this charge", example = "15000.00")
        BigDecimal totalAmount
) {}

