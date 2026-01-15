package com.restaurantqr.menu;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MenuCategoryDto", description = "Menu category displayed to the customer")
public record MenuCategoryDto(
        @Schema(description = "Category ID", example = "10")
        Long id,

        @Schema(description = "Category name", example = "Drinks")
        String name,

        @Schema(description = "Display order in the menu", example = "1")
        Integer displayOrder
) {}
