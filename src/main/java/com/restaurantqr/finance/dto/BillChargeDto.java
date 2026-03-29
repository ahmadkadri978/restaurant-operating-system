package com.restaurantqr.finance.dto;

import com.restaurantqr.finance.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "A single charge line on a session bill")
public record BillChargeDto(
        @Schema(description = "Charge name in Arabic", example = "ضريبة القيمة المضافة")
        String nameAr,

        @Schema(description = "Charge name in English", example = "VAT")
        String nameEn,

        @Schema(description = "Rule type", example = "PERCENTAGE")
        RuleType ruleType,

        @Schema(description = "Rule value (rate or fixed amount)", example = "10.0000")
        BigDecimal ruleValue,

        @Schema(description = "Whether this is a compound charge")
        boolean isCompound,

        @Schema(description = "Computed monetary amount for this charge", example = "1000.00")
        BigDecimal computedAmount
) {}

