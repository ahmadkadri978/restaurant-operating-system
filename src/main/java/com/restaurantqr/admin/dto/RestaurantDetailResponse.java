package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Complete restaurant details including all related data")
public record RestaurantDetailResponse(

        @Schema(description = "Restaurant ID", example = "1")
        Long restaurantId,

        @Schema(description = "Restaurant name", example = "Bella Italia")
        String restaurantName,

        @Schema(description = "Unique restaurant code", example = "BELLA-001")
        String restaurantCode,

        @Schema(description = "Whether the restaurant is active", example = "true")
        boolean isActive,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt,

        @Schema(description = "Menu categories with items")
        List<CategoryDetailResponse> categories,

        @Schema(description = "Restaurant tables")
        List<TableDetailResponse> tables,

        @Schema(description = "Restaurant users")
        List<UserDetailResponse> users
) {}

