package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response after restaurant deletion")
public record DeleteRestaurantResponse(

        @Schema(description = "Deletion result message", example = "Restaurant BELLA-001 successfully deactivated")
        String message,

        @Schema(description = "Restaurant ID that was deleted", example = "1")
        Long restaurantId,

        @Schema(description = "Restaurant code that was deleted", example = "BELLA-001")
        String restaurantCode,

        @Schema(description = "Whether this was a hard delete (permanent)", example = "false")
        boolean hardDelete,

        @Schema(description = "Number of categories deleted/deactivated", example = "5")
        int deletedCategories,

        @Schema(description = "Number of menu items deleted/deactivated", example = "25")
        int deletedMenuItems,

        @Schema(description = "Number of tables deleted/deactivated", example = "10")
        int deletedTables,

        @Schema(description = "Number of users deleted/deactivated", example = "4")
        int deletedUsers,

        @Schema(description = "Deletion timestamp")
        Instant deletedAt
) {}

