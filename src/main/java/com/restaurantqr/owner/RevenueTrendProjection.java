package com.restaurantqr.owner;

import java.math.BigDecimal;

/**
 * Projection interface for revenue trend data points.
 * Timestamp is returned as Unix timestamp (seconds since epoch) for reliable MySQL compatibility.
 */
public interface RevenueTrendProjection {
    Long getTimestamp();  // Unix timestamp in seconds
    BigDecimal getRevenue();
    Long getOrderCount();
}

