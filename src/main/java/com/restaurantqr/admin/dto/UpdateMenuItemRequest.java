package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Menu item update request")
public record UpdateMenuItemRequest(

        @Schema(description = "Item ID (if updating existing item)", example = "1")
        Long itemId,

        @Schema(description = "Item name", example = "Margherita")
        @NotBlank(message = "Item name is required")
        @Size(min = 2, max = 100, message = "Item name must be between 2 and 100 characters")
        String name,

        @Schema(description = "Item description", example = "Tomato, Mozzarella, Basil")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Schema(description = "Price", example = "12.50")
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimal places")
        BigDecimal price,

        @Schema(description = "Image URL", example = "/uploads/menu/margherita.jpg")
        @Size(max = 500, message = "Image URL must not exceed 500 characters")
        String imageUrl,

        @Schema(description = "Display order (lower = first)", example = "1")
        Integer displayOrder,

        @Schema(description = "Whether the item is available for ordering", example = "true")
        Boolean isAvailable,

        @Schema(description = "Whether the item is active", example = "true")
        Boolean isActive
) {
    public boolean isAvailableOrDefault() {
        return isAvailable == null || isAvailable;
    }

    public boolean isActiveOrDefault() {
        return isActive == null || isActive;
    }
}

