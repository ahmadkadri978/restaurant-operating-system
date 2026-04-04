package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "SessionAnalyticsDto", description = "Session-based analytics for the owner dashboard")
public record SessionAnalyticsDto(

        @Schema(description = "Total sessions opened in the period", example = "85")
        Long totalSessions,

        @Schema(description = "Sessions still open (not yet closed) in the period", example = "3")
        Long openSessionsCount,

        @Schema(description = "Closed sessions in the period", example = "82")
        Long closedSessionsCount,

        @Schema(description = "Average session duration in minutes (closed sessions only)", example = "47")
        Integer avgSessionDurationMinutes,

        @Schema(description = "Longest session duration in minutes", example = "180")
        Integer longestSessionMinutes,

        @Schema(description = "Shortest session duration in minutes", example = "5")
        Integer shortestSessionMinutes,

        @Schema(description = "Total occupied time across all tables in minutes", example = "3854")
        Long totalOccupiedMinutes,

        @Schema(description = "Top tables ranked by session count")
        List<SessionTopTableDto> topTablesBySessions,

        @Schema(description = "Peak hours by session openings")
        List<SessionPeakHourDto> peakSessionHours,

        @Schema(description = "Currently open tables (live snapshot)")
        List<OpenTableInfoDto> currentOpenTables
) {}

