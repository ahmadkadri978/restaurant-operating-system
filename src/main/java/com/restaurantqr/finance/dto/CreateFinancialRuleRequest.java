package com.restaurantqr.finance.dto;

import com.restaurantqr.finance.RuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Request to create a new financial rule for a restaurant")
public record CreateFinancialRuleRequest(
        @Schema(description = "Arabic display name (required)", example = "ضريبة القيمة المضافة")
        @NotBlank(message = "nameAr is required")
        @Size(max = 255, message = "nameAr must not exceed 255 characters")
        String nameAr,

        @Schema(description = "English display name (optional)", example = "VAT")
        @Size(max = 255, message = "nameEn must not exceed 255 characters")
        String nameEn,

        @Schema(description = "Rule type: FIXED or PERCENTAGE", example = "PERCENTAGE")
        @NotNull(message = "ruleType is required")
        RuleType ruleType,

        @Schema(description = "Value: fixed amount (> 0) or percentage (0.01–100.00)", example = "10.00")
        @NotNull(message = "value is required")
        @DecimalMin(value = "0.0001", message = "value must be greater than 0")
        BigDecimal value,

        @Schema(description = "Whether percentage is compound (applied on subtotal + prior charges). Only relevant for PERCENTAGE type.")
        Boolean isCompound
) {}

