package com.restaurantqr.menu;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "MenuItemDto", description = "Menu item displayed to the customer")
public record MenuItemDto(
        @Schema(description = "Item ID", example = "100")
        Long id,

        @Schema(description = "Category ID that this item belongs to", example = "10")
        Long categoryId,

        @Schema(description = "Item name", example = "Cola")
        String name,

        @Schema(description = "Item description", example = "Cold drink")
        String description,

        @Schema(description = "Item price", example = "2.50")
        BigDecimal price,

        @Schema(description = "Availability flag (false = visible but not selectable)", example = "true")
        boolean isAvailable,

        @Schema(description = "Active flag (false = hidden from menu)", example = "true")
        boolean isActive,

        @Schema(description = "Display order within category/list", example = "1")
        Integer displayOrder,

        @Schema(description = "Item image path", example = "/uploads/restaurant/item.jpg")
        String imageUrl,

        @Schema(description = "Last update timestamp in milliseconds (for cache busting)", example = "1710672000000")
        Long updatedAt
) {}
