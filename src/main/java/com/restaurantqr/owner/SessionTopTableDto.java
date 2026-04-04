package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionTopTableDto", description = "Table ranked by session count")
public record SessionTopTableDto(
        @Schema(description = "Table ID", example = "5")
        Long tableId,

        @Schema(description = "Table number", example = "12")
        Integer tableNumber,

        @Schema(description = "Number of sessions at this table", example = "18")
        Long sessionCount,

        @Schema(description = "Total occupied minutes at this table", example = "540")
        Long totalOccupiedMinutes
) {}

