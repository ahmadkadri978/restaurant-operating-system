package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "OpenTableInfoDto", description = "Currently open table session info (live snapshot)")
public record OpenTableInfoDto(
        @Schema(description = "Table ID", example = "5")
        Long tableId,

        @Schema(description = "Table number", example = "12")
        Integer tableNumber,

        @Schema(description = "When the session was opened", example = "2026-03-12T18:30:00Z")
        Instant sessionOpenedAt,

        @Schema(description = "Current session duration in minutes", example = "42")
        Long durationMinutes
) {}

