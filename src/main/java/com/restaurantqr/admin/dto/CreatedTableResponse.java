package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created table information with QR token")
public record CreatedTableResponse(

        @Schema(description = "Table ID", example = "25")
        Long tableId,

        @Schema(description = "Table number", example = "1")
        Integer tableNumber,

        @Schema(description = "Unique QR token for this table", example = "BELLA-001-T1-550e8400-e29b-41d4-a716-446655440000")
        String qrToken
) {}

