package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Request to update a restaurant and its related data")
public record UpdateRestaurantRequest(

        @Schema(description = "Restaurant name", example = "Bella Italia Updated")
        @NotBlank(message = "Restaurant name is required")
        @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
        String restaurantName,

        @Schema(description = "Unique restaurant code", example = "BELLA-001")
        @NotBlank(message = "Restaurant code is required")
        @Size(min = 2, max = 20, message = "Restaurant code must be between 2 and 20 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Restaurant code must contain only uppercase letters, numbers, underscores and hyphens")
        String restaurantCode,

        @Schema(description = "Whether the restaurant is active", example = "true")
        Boolean isActive,

        @Schema(description = "Menu categories with items (items with categoryId will be updated, without will be created)")
        @Valid
        List<UpdateCategoryRequest> categories,

        @Schema(description = "Restaurant tables (tables with tableId will be updated, without will be created)")
        @Valid
        List<UpdateTableRequest> tables,

        @Schema(description = "Restaurant users (users with userId will be updated, without will be created)")
        @Valid
        List<UpdateUserRequest> users,

        @Schema(description = "If true, delete categories/items/tables/users that are not in the request", example = "false")
        Boolean deleteOrphaned
) {
    public boolean shouldDeleteOrphaned() {
        return deleteOrphaned != null && deleteOrphaned;
    }

    public boolean isActiveOrDefault() {
        return isActive == null || isActive;
    }
}

