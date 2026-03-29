package com.restaurantqr.owner;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "OverviewDto", description = "Overall statistics for the selected period")
public record OverviewDto(
        @Schema(description = "Total number of orders (all statuses)", example = "150")
        Long totalOrders,

        @Schema(description = "Number of NEW orders", example = "10")
        Long newOrders,

        @Schema(description = "Number of SENT_TO_KITCHEN orders", example = "15")
        Long sentToKitchenOrders,

        @Schema(description = "Number of DONE orders", example = "120")
        Long doneOrders,

        @Schema(description = "Number of CANCELLED orders", example = "5")
        Long cancelledOrders,

        @Schema(description = "Completion rate (DONE / (DONE + CANCELLED)), 0-100", example = "96.0")
        BigDecimal completionRate,

        @Schema(description = "Cancellation rate (CANCELLED / total), 0-100", example = "3.33")
        BigDecimal cancellationRate,

        @Schema(description = "Gross revenue from items only (excluding cancelled orders)", example = "1234.56")
        BigDecimal grossRevenue,

        @Schema(description = "Average order value (items only)", example = "10.29")
        BigDecimal averageOrderValue,

        @Schema(description = "Number of active tables in restaurant", example = "20")
        Long activeTablesCount,

        @Schema(description = "Number of tables with orders in period", example = "15")
        Long tablesWithOrdersCount,

        @Schema(description = "Number of service calls in the period", example = "23")
        Long serviceCallsCount,

        @Schema(description = "Total billed revenue (items + charges from finalized bills)", example = "1500.00")
        BigDecimal totalBilledRevenue,

        @Schema(description = "Total charges/taxes revenue from finalized bills", example = "265.44")
        BigDecimal totalChargesRevenue,

        @Schema(description = "Average bill value per finalized session", example = "125.00")
        BigDecimal averageBillValue,

        @Schema(description = "Number of finalized bills in the period", example = "12")
        Long finalizedBillsCount
) {}



