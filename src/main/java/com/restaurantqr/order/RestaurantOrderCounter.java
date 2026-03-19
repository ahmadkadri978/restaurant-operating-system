package com.restaurantqr.order;

import jakarta.persistence.*;

/**
 * Counter entity for generating restaurant-specific order numbers.
 * Each restaurant has its own counter that increments atomically.
 */
@Entity
@Table(name = "restaurant_order_counters")
public class RestaurantOrderCounter {

    @Id
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "last_order_number", nullable = false)
    private Integer lastOrderNumber = 0;

    public RestaurantOrderCounter() {
    }

    public RestaurantOrderCounter(Long restaurantId, Integer lastOrderNumber) {
        this.restaurantId = restaurantId;
        this.lastOrderNumber = lastOrderNumber;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Integer getLastOrderNumber() {
        return lastOrderNumber;
    }

    public void setLastOrderNumber(Integer lastOrderNumber) {
        this.lastOrderNumber = lastOrderNumber;
    }
}

