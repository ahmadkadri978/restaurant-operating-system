package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "DateRangeDto", description = "Date range for the dashboard period")
public record DateRangeDto(
        @Schema(description = "Start of the period (inclusive)", example = "2026-03-12T00:00:00Z")
        Instant startDate,

        @Schema(description = "End of the period (exclusive)", example = "2026-03-13T00:00:00Z")
        Instant endDate,

        @Schema(description = "Period type", example = "DAY")
        DashboardPeriod period
) {}

