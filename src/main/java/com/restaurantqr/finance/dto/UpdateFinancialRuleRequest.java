package com.restaurantqr.finance.dto;

import com.restaurantqr.finance.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Request to update an existing financial rule")
public record UpdateFinancialRuleRequest(
        @Schema(description = "Arabic display name", example = "ضريبة القيمة المضافة")
        @Size(max = 255, message = "nameAr must not exceed 255 characters")
        String nameAr,

        @Schema(description = "English display name", example = "VAT")
        @Size(max = 255, message = "nameEn must not exceed 255 characters")
        String nameEn,

        @Schema(description = "Rule type: FIXED or PERCENTAGE", example = "PERCENTAGE")
        RuleType ruleType,

        @Schema(description = "Value: fixed amount (> 0) or percentage (0.01–100.00)", example = "10.00")
        @DecimalMin(value = "0.0001", message = "value must be greater than 0")
        BigDecimal value,

        @Schema(description = "Whether percentage is compound")
        Boolean isCompound,

        @Schema(description = "Whether the rule is active")
        Boolean isActive
) {}

