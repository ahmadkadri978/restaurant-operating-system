package com.restaurantqr.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Repository for managing restaurant order counters.
 * Used for atomic generation of restaurant-specific display order numbers.
 */
public interface RestaurantOrderCounterRepository extends JpaRepository<RestaurantOrderCounter, Long> {

    /**
     * Find and lock the counter row for a restaurant.
     * Uses PESSIMISTIC_WRITE to ensure exclusive access during the transaction.
     * This prevents race conditions when multiple orders are created simultaneously.
     *
     * @param restaurantId the restaurant ID
     * @return the locked counter entity
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM RestaurantOrderCounter c WHERE c.restaurantId = :restaurantId")
    Optional<RestaurantOrderCounter> findByIdForUpdate(@Param("restaurantId") Long restaurantId);
}

