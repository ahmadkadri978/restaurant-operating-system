package com.restaurantqr.order;

import com.restaurantqr.order.staff.OrderStatus;
import com.restaurantqr.owner.PeakHourProjection;
import com.restaurantqr.owner.RevenueTrendProjection;
import com.restaurantqr.owner.StatusCountDto;
import com.restaurantqr.owner.TopTableDto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
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

    // ==================== OWNER DASHBOARD QUERIES ====================

    /**
     * Count all orders for restaurant in date range.
     */
    @Query("""
        select count(o)
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
    """)
    Long countOrdersByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Count orders by status for restaurant in date range.
     */
    @Query("""
        select count(o)
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.status = :status
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
    """)
    Long countOrdersByRestaurantAndStatusAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("status") OrderStatus status,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Sum total revenue (excluding CANCELLED orders) for restaurant in date range.
     */
    @Query("""
        select coalesce(sum(o.totalAmount), 0)
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.status != 'CANCELLED'
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
    """)
    BigDecimal sumRevenueByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get order counts grouped by status.
     */
    @Query("""
        select new com.restaurantqr.owner.StatusCountDto(o.status, count(o))
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
        group by o.status
        order by count(o) desc
    """)
    List<StatusCountDto> countOrdersGroupedByStatus(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Count distinct tables that have orders in date range.
     */
    @Query("""
        select count(distinct o.table.id)
        from CustomerOrder o
        where o.restaurant.id = :restaurantId
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
    """)
    Long countDistinctTablesWithOrders(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get top tables by order count (excluding CANCELLED).
     */
    @Query("""
        select new com.restaurantqr.owner.TopTableDto(
            t.id,
            t.tableNumber,
            count(o),
            coalesce(sum(o.totalAmount), 0)
        )
        from CustomerOrder o
        join o.table t
        where o.restaurant.id = :restaurantId
          and o.status != 'CANCELLED'
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
        group by t.id, t.tableNumber
        order by count(o) desc
    """)
    List<TopTableDto> findTopTablesByOrderCount(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    /**
     * Get revenue trend grouped by hour (for DAY period).
     * MySQL native query using DATE_FORMAT to extract hour.
     */
    @Query(value = """
        SELECT 
            DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(created_at)), '%Y-%m-%d %H:00:00') as timestamp,
            COALESCE(SUM(total_amount), 0) as revenue,
            COUNT(*) as orderCount
        FROM customer_orders
        WHERE restaurant_id = :restaurantId
          AND status != 'CANCELLED'
          AND created_at >= :startDate
          AND created_at < :endDate
        GROUP BY DATE_FORMAT(FROM_UNIXTIME(UNIX_TIMESTAMP(created_at)), '%Y-%m-%d %H:00:00')
        ORDER BY timestamp ASC
    """, nativeQuery = true)
    List<RevenueTrendProjection> findRevenueTrendByHour(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get revenue trend grouped by day (for WEEK/MONTH period).
     * MySQL native query using DATE to group by day.
     */
    @Query(value = """
        SELECT 
            DATE(created_at) as timestamp,
            COALESCE(SUM(total_amount), 0) as revenue,
            COUNT(*) as orderCount
        FROM customer_orders
        WHERE restaurant_id = :restaurantId
          AND status != 'CANCELLED'
          AND created_at >= :startDate
          AND created_at < :endDate
        GROUP BY DATE(created_at)
        ORDER BY timestamp ASC
    """, nativeQuery = true)
    List<RevenueTrendProjection> findRevenueTrendByDay(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get revenue trend grouped by month (for YEAR period).
     * MySQL native query using DATE_FORMAT to extract year-month.
     */
    @Query(value = """
        SELECT 
            DATE_FORMAT(created_at, '%Y-%m-01') as timestamp,
            COALESCE(SUM(total_amount), 0) as revenue,
            COUNT(*) as orderCount
        FROM customer_orders
        WHERE restaurant_id = :restaurantId
          AND status != 'CANCELLED'
          AND created_at >= :startDate
          AND created_at < :endDate
        GROUP BY DATE_FORMAT(created_at, '%Y-%m')
        ORDER BY timestamp ASC
    """, nativeQuery = true)
    List<RevenueTrendProjection> findRevenueTrendByMonth(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );

    /**
     * Get peak hours (hour of day with most orders).
     * MySQL native query using HOUR() function.
     */
    @Query(value = """
        SELECT 
            HOUR(created_at) as hour,
            COUNT(*) as orderCount,
            COALESCE(SUM(total_amount), 0) as revenue
        FROM customer_orders
        WHERE restaurant_id = :restaurantId
          AND status != 'CANCELLED'
          AND created_at >= :startDate
          AND created_at < :endDate
        GROUP BY HOUR(created_at)
        ORDER BY orderCount DESC
    """, nativeQuery = true)
    List<PeakHourProjection> findPeakHoursByOrderCount(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate
    );
}
