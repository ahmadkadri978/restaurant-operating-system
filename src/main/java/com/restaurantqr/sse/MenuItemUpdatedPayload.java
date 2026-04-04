package com.restaurantqr.sse;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Payload for MENU_ITEM_UPDATED SSE events.
 * Contains all menu item fields needed for real-time updates on customer screens.
 */
@Schema(description = "Menu item update payload for SSE")
public record MenuItemUpdatedPayload(
        @Schema(description = "Menu item ID")
        Long itemId,

        @Schema(description = "Category ID this item belongs to")
        Long categoryId,

        @Schema(description = "Item name")
        String name,

        @Schema(description = "Item description")
        String description,

        @Schema(description = "Item price")
        BigDecimal price,

        @Schema(description = "Image URL")
        String imageUrl,

        @Schema(description = "Last update timestamp in epoch milliseconds (for cache busting)")
        Long updatedAt,

        @Schema(description = "Whether the item is available (false = visible but not selectable)")
        boolean isAvailable,

        @Schema(description = "Whether the item is active (false = completely hidden from menu)")
        boolean isActive,

        @Schema(description = "Display order within category")
        Integer displayOrder
) {
    /**
     * Creates a payload from a MenuItem entity.
     */
    public static MenuItemUpdatedPayload from(
            Long itemId,
            Long categoryId,
            String name,
            String description,
            BigDecimal price,
            String imageUrl,
            Long updatedAtMillis,
            boolean isAvailable,
            boolean isActive,
            Integer displayOrder
    ) {
        return new MenuItemUpdatedPayload(
                itemId,
                categoryId,
                name,
                description,
                price,
                imageUrl,
                updatedAtMillis,
                isAvailable,
                isActive,
                displayOrder
        );
    }
}

