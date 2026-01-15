package com.restaurantqr.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Page<CustomerOrder> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    Page<CustomerOrder> findByRestaurantIdAndTableIdOrderByCreatedAtDesc(Long restaurantId, Long tableId, Pageable pageable);

    boolean existsByTableIdAndCreatedAtAfter(Long tableId, Instant threshold);

    // ✅ Polling: bring only orders created after 'since'
    @Query("""
        select o
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt > :since
        order by o.createdAt asc
    """)
    List<CustomerOrder> findNewOrdersSince(Long restaurantId, Instant since);

    //  Polling + paging
    @Query("""
        select o
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt > :since
    """)
    Page<CustomerOrder> findNewOrdersSince(Long restaurantId, Instant since, Pageable pageable);
}

