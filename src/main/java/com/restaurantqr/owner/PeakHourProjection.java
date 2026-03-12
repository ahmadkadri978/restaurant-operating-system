package com.restaurantqr.owner;

import java.math.BigDecimal;

/**
 * Projection interface for peak hours data.
 */
public interface PeakHourProjection {
    Integer getHour();
    Long getOrderCount();
    BigDecimal getRevenue();
}

