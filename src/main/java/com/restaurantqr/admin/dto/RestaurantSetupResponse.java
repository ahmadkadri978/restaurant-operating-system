package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Response after successful restaurant setup creation")
public record RestaurantSetupResponse(

        @Schema(description = "Created restaurant ID", example = "5")
        Long restaurantId,

        @Schema(description = "Restaurant name", example = "Bella Italia")
        String restaurantName,

        @Schema(description = "Restaurant code", example = "BELLA-001")
        String restaurantCode,

        @Schema(description = "Number of categories created", example = "3")
        int createdCategories,

        @Schema(description = "Number of menu items created", example = "15")
        int createdMenuItems,

        @Schema(description = "Created tables with QR tokens")
        List<CreatedTableResponse> createdTables,

        @Schema(description = "Created users")
        List<CreatedUserResponse> createdUsers,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {}


