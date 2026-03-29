package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Request to reorder financial rules")
public record ReorderFinancialRulesRequest(
        @Schema(description = "Ordered list of rule IDs (first = apply_order 1, second = 2, etc.)")
        @NotNull(message = "ruleIds is required")
        @NotEmpty(message = "ruleIds must not be empty")
        List<Long> ruleIds
) {}

