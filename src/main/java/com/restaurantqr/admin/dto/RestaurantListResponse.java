package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated list of restaurants")
public record RestaurantListResponse(

        @Schema(description = "List of restaurants")
        List<RestaurantListItemResponse> content,

        @Schema(description = "Total number of restaurants", example = "15")
        long totalElements,

        @Schema(description = "Total number of pages", example = "2")
        int totalPages,

        @Schema(description = "Current page number (0-based)", example = "0")
        int currentPage,

        @Schema(description = "Page size", example = "20")
        int pageSize
) {}

