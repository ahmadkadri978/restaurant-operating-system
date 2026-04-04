package com.restaurantqr.sse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for MENU_ITEM_DELETED SSE events.
 */
@Schema(description = "Menu item deleted payload for SSE")
public record MenuItemDeletedPayload(
        @Schema(description = "Deleted menu item ID")
        Long itemId,

        @Schema(description = "Category ID this item belonged to")
        Long categoryId
) {}

