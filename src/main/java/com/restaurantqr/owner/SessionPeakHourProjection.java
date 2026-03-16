package com.restaurantqr.owner;

/**
 * Projection interface for peak session hours (native query).
 */
public interface SessionPeakHourProjection {
    Integer getHour();
    Long getSessionCount();
}

