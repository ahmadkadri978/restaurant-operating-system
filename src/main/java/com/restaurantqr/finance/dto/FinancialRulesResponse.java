package com.restaurantqr.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "List of financial rules for a restaurant")
public record FinancialRulesResponse(
        @Schema(description = "Restaurant ID")
        Long restaurantId,

        @Schema(description = "Financial rules ordered by apply_order")
        List<FinancialRuleDto> rules
) {}

