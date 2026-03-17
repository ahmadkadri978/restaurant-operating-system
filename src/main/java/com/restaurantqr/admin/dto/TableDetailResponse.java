package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Restaurant table details")
public record TableDetailResponse(

        @Schema(description = "Table ID", example = "25")
        Long tableId,

        @Schema(description = "Table number", example = "1")
        Integer tableNumber,

        @Schema(description = "Table label/name", example = "Window Seat 1")
        String label,

        @Schema(description = "Unique QR token", example = "BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000")
        String qrToken,

        @Schema(description = "Whether the table is active", example = "true")
        boolean isActive
) {}

