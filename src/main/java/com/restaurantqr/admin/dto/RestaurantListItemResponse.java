package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Restaurant summary for list view")
public record RestaurantListItemResponse(

        @Schema(description = "Restaurant ID", example = "1")
        Long restaurantId,

        @Schema(description = "Restaurant name", example = "Bella Italia")
        String restaurantName,

        @Schema(description = "Unique restaurant code", example = "BELLA-001")
        String restaurantCode,

        @Schema(description = "Whether the restaurant is active", example = "true")
        boolean isActive,

        @Schema(description = "Number of menu categories", example = "5")
        int categoryCount,

        @Schema(description = "Number of menu items", example = "25")
        int menuItemCount,

        @Schema(description = "Number of tables", example = "10")
        int tableCount,

        @Schema(description = "Number of users", example = "4")
        int userCount,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {}

