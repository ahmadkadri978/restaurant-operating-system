package com.restaurantqr.finance;

import com.restaurantqr.finance.dto.BillPreviewDto;
import com.restaurantqr.finance.dto.SessionBillDto;

public interface BillCalculationService {

    /**
     * Compute or retrieve the bill for a session.
     * - If session is open: computes in real-time (DRAFT), does NOT persist.
     * - If session is closed and bill exists: returns the finalized snapshot.
     * - If session is closed with no bill: computes and finalizes retroactively.
     *
     * @param tableSessionId the table_sessions.id
     * @return the bill DTO
     */
    SessionBillDto getOrComputeBill(Long tableSessionId);

    /**
     * Called when a session is closed.
     * Finalizes the bill: snapshots all rules, computes totals, marks FINALIZED.
     * Idempotent: if already finalized, returns existing bill.
     *
     * @param tableSessionId the table_sessions.id
     * @return the persisted finalized bill
     */
    SessionBill finalizeBill(Long tableSessionId);

    /**
     * Compute a rich bill preview for an open session.
     * Includes aggregated order items, charges, and totals.
     * Does NOT persist anything — purely a read operation.
     *
     * @param tableSessionId the table_sessions.id
     * @return the bill preview DTO with items
     */
    BillPreviewDto computeBillPreview(Long tableSessionId);
}

