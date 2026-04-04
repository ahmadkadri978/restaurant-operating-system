package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "TopItemDto", description = "Top selling menu item statistics")
public record TopItemDto(
        @Schema(description = "Menu item ID", example = "42")
        Long menuItemId,

        @Schema(description = "Menu item name", example = "Margherita Pizza")
        String name,

        @Schema(description = "Total quantity sold", example = "45")
        Long quantitySold,

        @Schema(description = "Total revenue from this item", example = "450.00")
        BigDecimal totalRevenue
) {}

