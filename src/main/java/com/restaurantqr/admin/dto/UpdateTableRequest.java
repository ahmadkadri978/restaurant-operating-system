package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Table update request")
public record UpdateTableRequest(

        @Schema(description = "Table ID (if updating existing table)", example = "25")
        Long tableId,

        @Schema(description = "Table number (must be unique within restaurant)", example = "1")
        @NotNull(message = "Table number is required")
        @Min(value = 1, message = "Table number must be at least 1")
        Integer tableNumber,

        @Schema(description = "Optional table label/name for display", example = "Window Seat 1")
        @Size(max = 50, message = "Table label must not exceed 50 characters")
        String label,

        @Schema(description = "Whether the table is active", example = "true")
        Boolean isActive
) {
    public boolean isActiveOrDefault() {
        return isActive == null || isActive;
    }
}

