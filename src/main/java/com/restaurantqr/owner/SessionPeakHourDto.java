package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SessionPeakHourDto", description = "Peak hour by session openings")
public record SessionPeakHourDto(
        @Schema(description = "Hour of day (0-23)", example = "19")
        Integer hour,

        @Schema(description = "Number of sessions opened in this hour", example = "12")
        Long sessionCount
) {}

