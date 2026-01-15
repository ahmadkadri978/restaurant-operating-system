package com.restaurantqr.menu;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "MenuResponse", description = "Menu response resolved by table QR token")
public record MenuResponse(
        @Schema(description = "Restaurant name", example = "Demo Restaurant")
        String restaurantName,

        @Schema(description = "Table number", example = "5")
        Integer tableNumber,

        @Schema(description = "Active categories")
        List<MenuCategoryDto> categories,

        @Schema(description = "Active & available items")
        List<MenuItemDto> items
) {}
