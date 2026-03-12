package com.restaurantqr.owner;

import java.time.LocalDate;

public interface OwnerDashboardService {

    /**
     * Get complete dashboard statistics for the owner.
     *
     * @param restaurantId the restaurant ID from authenticated JWT principal (must not be null)
     * @param period the dashboard period (DAY, WEEK, MONTH, YEAR) (must not be null)
     * @param anchorDate the reference date for the period (if null, uses current date in provided zone)
     * @param zone the timezone ID for date calculations (e.g., "Europe/Berlin") (must not be null)
     * @return complete dashboard response with all statistics
     * @throws com.restaurantqr.exception.BusinessRuleException if parameters are invalid or restaurant not found
     * @throws java.time.DateTimeException if zone is invalid
     */
    OwnerDashboardResponse getDashboard(
            Long restaurantId,
            DashboardPeriod period,
            LocalDate anchorDate,
            String zone
    );
}

