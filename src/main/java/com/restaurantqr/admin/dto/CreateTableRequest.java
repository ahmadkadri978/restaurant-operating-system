package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Table to create")
public record CreateTableRequest(

        @Schema(description = "Table number (must be unique within restaurant)", example = "1")
        @NotNull(message = "Table number is required")
        @Min(value = 1, message = "Table number must be at least 1")
        Integer tableNumber,

        @Schema(description = "Optional table label/name for display", example = "Window Seat 1")
        @Size(max = 50, message = "Table label must not exceed 50 characters")
        String label
) {}


