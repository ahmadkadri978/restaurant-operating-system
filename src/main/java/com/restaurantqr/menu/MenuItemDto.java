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

        @Schema(description = "Availability flag (true means the kitchen can prepare it now)", example = "true")
        boolean isAvailable,

        @Schema(description = "Display order within category/list", example = "1")
        Integer displayOrder,
        @Schema(description = "bring item image path", example = "\\main\\resources\\static\\uploads\\menu")
        String imageUrl
) {}
