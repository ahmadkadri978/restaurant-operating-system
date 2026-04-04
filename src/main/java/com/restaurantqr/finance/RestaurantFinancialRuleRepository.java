package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RestaurantFinancialRuleRepository extends JpaRepository<RestaurantFinancialRule, Long> {

    /**
     * Find all rules for a restaurant, ordered by apply_order ascending.
     * Includes inactive rules (for management UI).
     */
    List<RestaurantFinancialRule> findByRestaurantIdOrderByApplyOrderAsc(Long restaurantId);

    /**
     * Find only active rules for a restaurant, ordered by apply_order ascending.
     * Used during bill calculation.
     */
    List<RestaurantFinancialRule> findByRestaurantIdAndIsActiveTrueOrderByApplyOrderAsc(Long restaurantId);

    /**
     * Find a rule by ID scoped to a restaurant (security boundary).
     */
    Optional<RestaurantFinancialRule> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Count active rules for a restaurant (used for limit validation).
     */
    @Query("SELECT COUNT(r) FROM RestaurantFinancialRule r WHERE r.restaurant.id = :restaurantId AND r.isActive = true")
    Long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Find the maximum apply_order for a restaurant (for auto-assigning next order).
     */
    @Query("SELECT COALESCE(MAX(r.applyOrder), 0) FROM RestaurantFinancialRule r WHERE r.restaurant.id = :restaurantId")
    Integer findMaxApplyOrderByRestaurantId(@Param("restaurantId") Long restaurantId);
}

