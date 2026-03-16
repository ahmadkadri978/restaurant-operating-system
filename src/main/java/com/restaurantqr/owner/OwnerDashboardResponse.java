package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "OwnerDashboardResponse", description = "Complete dashboard data for owner")
public record OwnerDashboardResponse(
        @Schema(description = "Restaurant ID", example = "1")
        Long restaurantId,

        @Schema(description = "Restaurant name", example = "Syrian Palace")
        String restaurantName,

        @Schema(description = "Selected period type", example = "DAY")
        DashboardPeriod period,

        @Schema(description = "Date range for this dashboard")
        DateRangeDto dateRange,

        @Schema(description = "Overall statistics")
        OverviewDto overview,

        @Schema(description = "Revenue trend over time")
        List<RevenuePointDto> revenueTrend,

        @Schema(description = "Order counts by status")
        List<StatusCountDto> ordersByStatus,

        @Schema(description = "Top selling items by quantity")
        List<TopItemDto> topItemsByQuantity,

        @Schema(description = "Top selling items by revenue")
        List<TopItemDto> topItemsByRevenue,

        @Schema(description = "Top tables by order count")
        List<TopTableDto> topTables,

        @Schema(description = "Service calls by type")
        List<ServiceCallTypeCountDto> serviceCallsByType,

        @Schema(description = "Peak hours by order count")
        List<PeakHourDto> peakHours,

        @Schema(description = "Table session analytics")
        SessionAnalyticsDto sessionAnalytics
) {}

