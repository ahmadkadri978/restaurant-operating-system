package com.restaurantqr.owner;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Projection interface for revenue trend data points.
 */
public interface RevenueTrendProjection {
    Instant getTimestamp();
    BigDecimal getRevenue();
    Long getOrderCount();
}

