package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionPaymentRepository extends JpaRepository<SessionPayment, Long> {

    Optional<SessionPayment> findByTableSessionId(Long tableSessionId);

    boolean existsByTableSessionId(Long tableSessionId);

    /**
     * Batch-load payments by multiple session IDs (avoids N+1 in invoice list).
     */
    List<SessionPayment> findByTableSessionIdIn(List<Long> tableSessionIds);

    // ==================== OWNER DASHBOARD AGGREGATION QUERIES ====================

    /**
     * Sum tip_amount of all payments for a restaurant in a date range.
     * Uses the session's openedAt as the time anchor (consistent with bill queries).
     */
    @Query("""
        SELECT COALESCE(SUM(p.tipAmount), 0)
        FROM SessionPayment p
        WHERE p.restaurant.id = :restaurantId
          AND p.tableSession.openedAt >= :startDate
          AND p.tableSession.openedAt < :endDate
    """)
    BigDecimal sumTipByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    // ==================== CASH REGISTER AGGREGATION QUERIES ====================

    /**
     * Sum amount_received for all payments linked to a cash register session.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amountReceived), 0)
        FROM SessionPayment p
        WHERE p.cashRegisterSession.id = :registerSessionId
    """)
    BigDecimal sumAmountReceivedByRegisterSession(@Param("registerSessionId") Long registerSessionId);

    /**
     * Sum tip_amount for all payments linked to a cash register session.
     */
    @Query("""
        SELECT COALESCE(SUM(p.tipAmount), 0)
        FROM SessionPayment p
        WHERE p.cashRegisterSession.id = :registerSessionId
    """)
    BigDecimal sumTipByRegisterSession(@Param("registerSessionId") Long registerSessionId);

    /**
     * Sum amount_returned for all payments linked to a cash register session.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amountReturned), 0)
        FROM SessionPayment p
        WHERE p.cashRegisterSession.id = :registerSessionId
    """)
    BigDecimal sumAmountReturnedByRegisterSession(@Param("registerSessionId") Long registerSessionId);

    /**
     * Count payments linked to a cash register session.
     */
    long countByCashRegisterSessionId(Long cashRegisterSessionId);
}

