package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionInvoiceRepository extends JpaRepository<SessionInvoice, Long> {

    /**
     * Find invoice by table_session_id (for idempotency check during generation).
     */
    Optional<SessionInvoice> findByTableSessionId(Long tableSessionId);

    /**
     * Find invoice by ID scoped to restaurant, with items eagerly fetched (for PDF).
     */
    @Query("""
        SELECT i FROM SessionInvoice i
        LEFT JOIN FETCH i.items
        WHERE i.id = :id
          AND i.restaurant.id = :restaurantId
    """)
    Optional<SessionInvoice> findByIdAndRestaurantIdWithItems(
            @Param("id") Long id,
            @Param("restaurantId") Long restaurantId
    );

    /**
     * List invoices for a restaurant in a date range, ordered newest first.
     */
    @Query("""
        SELECT i FROM SessionInvoice i
        WHERE i.restaurant.id = :restaurantId
          AND i.createdAt >= :start
          AND i.createdAt < :end
        ORDER BY i.createdAt DESC
    """)
    List<SessionInvoice> findByRestaurantIdAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );

    /**
     * Search invoice by restaurant and invoice number.
     */
    @Query("""
        SELECT i FROM SessionInvoice i
        WHERE i.restaurant.id = :restaurantId
          AND i.invoiceNumber = :invoiceNumber
    """)
    Optional<SessionInvoice> findByRestaurantIdAndInvoiceNumber(
            @Param("restaurantId") Long restaurantId,
            @Param("invoiceNumber") String invoiceNumber
    );

    /**
     * Count invoices for a restaurant on a given day (for daily sequence generation).
     */
    @Query("""
        SELECT COUNT(i) FROM SessionInvoice i
        WHERE i.restaurant.id = :restaurantId
          AND i.createdAt >= :dayStart
          AND i.createdAt < :dayEnd
    """)
    long countByRestaurantIdAndDay(
            @Param("restaurantId") Long restaurantId,
            @Param("dayStart") Instant dayStart,
            @Param("dayEnd") Instant dayEnd
    );
}

