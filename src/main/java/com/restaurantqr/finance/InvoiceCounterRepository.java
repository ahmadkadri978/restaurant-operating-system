package com.restaurantqr.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO restaurant_invoice_counters (restaurant_id, date_str, last_seq)
        VALUES (:restaurantId, :dateStr, 1)
        ON DUPLICATE KEY UPDATE last_seq = last_seq + 1
        """, nativeQuery = true)
    int reserveNextSequence(@Param("restaurantId") Long restaurantId,
                            @Param("dateStr") String dateStr);

    Optional<InvoiceCounter> findByRestaurantIdAndDateStr(Long restaurantId, String dateStr);
}

