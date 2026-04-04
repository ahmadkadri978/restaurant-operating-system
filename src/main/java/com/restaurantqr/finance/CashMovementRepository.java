package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    /**
     * List all movements for a register session.
     */
    List<CashMovement> findByCashRegisterSessionIdOrderByCreatedAtAsc(Long cashRegisterSessionId);

    /**
     * Sum CASH_IN movements for a register session.
     */
    @Query("""
        SELECT COALESCE(SUM(m.amount), 0)
        FROM CashMovement m
        WHERE m.cashRegisterSession.id = :registerSessionId
          AND m.type = com.restaurantqr.finance.CashMovementType.CASH_IN
    """)
    BigDecimal sumCashInByRegisterSession(@Param("registerSessionId") Long registerSessionId);

    /**
     * Sum CASH_OUT movements for a register session.
     */
    @Query("""
        SELECT COALESCE(SUM(m.amount), 0)
        FROM CashMovement m
        WHERE m.cashRegisterSession.id = :registerSessionId
          AND m.type = com.restaurantqr.finance.CashMovementType.CASH_OUT
    """)
    BigDecimal sumCashOutByRegisterSession(@Param("registerSessionId") Long registerSessionId);
}

