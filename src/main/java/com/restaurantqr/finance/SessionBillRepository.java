package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionBillRepository extends JpaRepository<SessionBill, Long> {

    /**
     * Find bill by table_session_id.
     */
    Optional<SessionBill> findByTableSessionId(Long tableSessionId);

    /**
     * Find bill by table_session_id with charges eagerly fetched.
     */
    @Query("""
        SELECT b FROM SessionBill b
        LEFT JOIN FETCH b.charges
        WHERE b.tableSession.id = :tableSessionId
    """)
    Optional<SessionBill> findByTableSessionIdWithCharges(@Param("tableSessionId") Long tableSessionId);

    /**
     * Check if a finalized bill exists for a session.
     */
    boolean existsByTableSessionIdAndStatus(Long tableSessionId, BillStatus status);

    // ==================== OWNER DASHBOARD AGGREGATION QUERIES ====================

    /**
     * Sum grand_total of all FINALIZED bills for a restaurant in a date range.
     * Uses the session's openedAt as the time anchor.
     */
    @Query("""
        SELECT COALESCE(SUM(b.grandTotal), 0)
        FROM SessionBill b
        WHERE b.restaurant.id = :restaurantId
          AND b.status = 'FINALIZED'
          AND b.tableSession.openedAt >= :startDate
          AND b.tableSession.openedAt < :endDate
    """)
    BigDecimal sumGrandTotalByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Sum charges_total of all FINALIZED bills for a restaurant in a date range.
     */
    @Query("""
        SELECT COALESCE(SUM(b.chargesTotal), 0)
        FROM SessionBill b
        WHERE b.restaurant.id = :restaurantId
          AND b.status = 'FINALIZED'
          AND b.tableSession.openedAt >= :startDate
          AND b.tableSession.openedAt < :endDate
    """)
    BigDecimal sumChargesTotalByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Count FINALIZED bills for a restaurant in a date range.
     */
    @Query("""
        SELECT COUNT(b)
        FROM SessionBill b
        WHERE b.restaurant.id = :restaurantId
          AND b.status = 'FINALIZED'
          AND b.tableSession.openedAt >= :startDate
          AND b.tableSession.openedAt < :endDate
    """)
    Long countFinalizedBillsByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Charges breakdown: sum computed_amount grouped by rule_name_ar for FINALIZED bills.
     */
    @Query("""
        SELECT new com.restaurantqr.owner.ChargeBreakdownDto(
            c.ruleNameAr,
            c.ruleType,
            COALESCE(SUM(c.computedAmount), 0)
        )
        FROM SessionBillCharge c
        JOIN c.sessionBill b
        WHERE b.restaurant.id = :restaurantId
          AND b.status = 'FINALIZED'
          AND b.tableSession.openedAt >= :startDate
          AND b.tableSession.openedAt < :endDate
        GROUP BY c.ruleNameAr, c.ruleType
        ORDER BY SUM(c.computedAmount) DESC
    """)
    List<com.restaurantqr.owner.ChargeBreakdownDto> findChargesBreakdownByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Sum items_subtotal of all FINALIZED bills for a restaurant in a date range.
     * Provides a billing-consistent items revenue figure (same source as charges/grand totals).
     */
    @Query("""
        SELECT COALESCE(SUM(b.itemsSubtotal), 0)
        FROM SessionBill b
        WHERE b.restaurant.id = :restaurantId
          AND b.status = 'FINALIZED'
          AND b.tableSession.openedAt >= :startDate
          AND b.tableSession.openedAt < :endDate
    """)
    BigDecimal sumItemsSubtotalByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}


