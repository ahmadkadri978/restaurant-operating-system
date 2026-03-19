package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Menu item details")
public record MenuItemDetailResponse(

        @Schema(description = "Item ID", example = "1")
        Long itemId,

        @Schema(description = "Item name", example = "Margherita")
        String name,

        @Schema(description = "Item description", example = "Tomato, Mozzarella, Basil")
        String description,

        @Schema(description = "Price", example = "12.50")
        BigDecimal price,

        @Schema(description = "Image URL", example = "/uploads/bella-italia-BELLA-001/margherita.jpg")
        String imageUrl,

        @Schema(description = "Display order", example = "1")
        Integer displayOrder,

        @Schema(description = "Whether the item is available for ordering", example = "true")
        boolean isAvailable,

        @Schema(description = "Whether the item is active", example = "true")
        boolean isActive,

        @Schema(description = "Last update timestamp in milliseconds (for cache busting)", example = "1710672000000")
        Long updatedAt
) {}

