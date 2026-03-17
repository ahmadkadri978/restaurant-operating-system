package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Menu category update request")
public record UpdateCategoryRequest(

        @Schema(description = "Category ID (if updating existing category)", example = "1")
        Long categoryId,

        @Schema(description = "Category name", example = "Pizzas")
        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
        String name,

        @Schema(description = "Display order (lower = first)", example = "1")
        Integer displayOrder,

        @Schema(description = "Whether the category is active", example = "true")
        Boolean isActive,

        @Schema(description = "Menu items in this category")
        @Size(max = 100, message = "Maximum 100 items per category")
        @Valid
        List<UpdateMenuItemRequest> items
) {
    public boolean isActiveOrDefault() {
        return isActive == null || isActive;
    }
}

