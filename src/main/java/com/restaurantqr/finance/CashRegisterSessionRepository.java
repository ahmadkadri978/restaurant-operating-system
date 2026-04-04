package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CashRegisterSessionRepository extends JpaRepository<CashRegisterSession, Long> {

    /**
     * Find the currently OPEN register session for a restaurant.
     * Business rule: at most one OPEN session per restaurant at a time.
     */
    Optional<CashRegisterSession> findByRestaurantIdAndStatus(Long restaurantId, CashRegisterStatus status);

    /**
     * Check if a restaurant has an open register session.
     */
    boolean existsByRestaurantIdAndStatus(Long restaurantId, CashRegisterStatus status);

    /**
     * List closed register sessions for a restaurant, ordered by most recent first.
     */
    List<CashRegisterSession> findByRestaurantIdAndStatusOrderByClosedAtDesc(Long restaurantId, CashRegisterStatus status);

    /**
     * Paginated version: closed register sessions for a restaurant.
     * Ordering is delegated to the Pageable (caller passes Sort.by("closedAt").descending()).
     */
    Page<CashRegisterSession> findByRestaurantIdAndStatus(Long restaurantId, CashRegisterStatus status, Pageable pageable);

    /**
     * List register sessions for a restaurant within a date range.
     */
    @Query("""
        SELECT r FROM CashRegisterSession r
        WHERE r.restaurant.id = :restaurantId
          AND r.openedAt >= :startDate
          AND r.openedAt < :endDate
        ORDER BY r.openedAt DESC
    """)
    List<CashRegisterSession> findByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}

