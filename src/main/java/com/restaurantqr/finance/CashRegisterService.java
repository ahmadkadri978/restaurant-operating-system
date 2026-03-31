package com.restaurantqr.finance;

import com.restaurantqr.finance.dto.CashMovementDto;
import com.restaurantqr.finance.dto.CashRegisterDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CashRegisterService {

    /**
     * Open a new cash register session for the restaurant.
     * Only one OPEN session per restaurant at a time.
     */
    CashRegisterDto openRegister(Long restaurantId, Long userId, BigDecimal openingBalance);

    /**
     * Get the current (OPEN) register session for the restaurant, with running totals.
     * Returns empty if no register is open.
     */
    Optional<CashRegisterDto> getCurrentRegister(Long restaurantId);

    /**
     * Record a cash movement (CASH_IN or CASH_OUT) on the open register.
     */
    CashMovementDto recordMovement(Long restaurantId, Long userId, String type, BigDecimal amount, String reason);

    /**
     * Close the open register session with the actual closing balance.
     * Computes expected, discrepancy, and finalizes the session.
     */
    CashRegisterDto closeRegister(Long restaurantId, Long userId, BigDecimal actualClosing, String notes);

    /**
     * List closed register sessions (history) for the owner.
     */
    List<CashRegisterDto> getRegisterHistory(Long restaurantId);

    /**
     * Get a specific register session by ID (with full details).
     */
    CashRegisterDto getRegisterById(Long restaurantId, Long registerSessionId);
}

