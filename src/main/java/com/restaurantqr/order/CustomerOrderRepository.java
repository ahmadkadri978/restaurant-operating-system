package com.restaurantqr.order;

import com.restaurantqr.order.staff.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

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
    List<CustomerOrder> findNewOrdersSince(
            @Param("restaurantId") Long restaurantId,
            @Param("since") Instant since
    );

    // ✅ Polling + paging
    @Query("""
        select o
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt > :since
    """)
    Page<CustomerOrder> findNewOrdersSince(
            @Param("restaurantId") Long restaurantId,
            @Param("since") Instant since,
            Pageable pageable
    );



    // ✅ Paging list (status) with table fetched to avoid N+1 during mapping
    @Query("""
        select o
        from CustomerOrder o
        join fetch o.table t
        where o.restaurant.id = :restaurantId
          and o.status = :status
        order by o.createdAt desc
    """)
    Page<CustomerOrder> findPageByRestaurantAndStatusFetchTable(
            @Param("restaurantId") Long restaurantId,
            @Param("status") OrderStatus status,
            Pageable pageable
    );

    @Query("""
        select o
        from CustomerOrder o
        join fetch o.table t
        where o.restaurant.id = :restaurantId
          and o.status = :status
          and o.createdAt > :since
        order by o.createdAt desc
    """)
    Page<CustomerOrder> findPageByRestaurantAndStatusAndCreatedAtAfterFetchTable(
            @Param("restaurantId") Long restaurantId,
            @Param("status") OrderStatus status,
            @Param("since") Instant since,
            Pageable pageable
    );

    // ✅ Fetch details (table fetched) + restaurant boundary in DB (no extra if-check)
    @Query("""
        select o
        from CustomerOrder o
        join fetch o.table t
        where o.id = :orderId
          and o.restaurant.id = :restaurantId
    """)
    Optional<CustomerOrder> findByIdAndRestaurantIdFetchTable(
            @Param("restaurantId") Long restaurantId,
            @Param("orderId") Long orderId
    );

    // ✅ For status transitions: lock row to avoid double updates under pressure
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select o
        from CustomerOrder o
        where o.id = :orderId
          and o.restaurant.id = :restaurantId
    """)
    Optional<CustomerOrder> findByIdAndRestaurantIdForUpdate(
            @Param("restaurantId") Long restaurantId,
            @Param("orderId") Long orderId
    );
}

