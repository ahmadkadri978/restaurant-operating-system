package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Menu category details with items")
public record CategoryDetailResponse(

        @Schema(description = "Category ID", example = "1")
        Long categoryId,

        @Schema(description = "Category name", example = "Pizzas")
        String name,

        @Schema(description = "Display order", example = "1")
        Integer displayOrder,

        @Schema(description = "Whether the category is active", example = "true")
        boolean isActive,

        @Schema(description = "Menu items in this category")
        List<MenuItemDetailResponse> items
) {}

