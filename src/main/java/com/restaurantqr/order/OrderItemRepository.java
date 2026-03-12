package com.restaurantqr.order;

import com.restaurantqr.owner.TopItemDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderId(Long orderId);

    // ✅ For order details screen: avoid N+1 when accessing menuItem fields
    @Query("""
        select oi
        from OrderItem oi
        join fetch oi.menuItem mi
        where oi.order.id = :orderId
        order by oi.id asc
    """)
    List<OrderItem> findByOrderIdWithMenuItem(@Param("orderId") Long orderId);

    // ==================== OWNER DASHBOARD QUERIES ====================

    /**
     * Get top menu items by quantity sold (excluding CANCELLED orders).
     */
    @Query("""
        select new com.restaurantqr.owner.TopItemDto(
            m.id,
            m.name,
            sum(oi.quantity),
            coalesce(sum(oi.totalPrice), 0)
        )
        from OrderItem oi
        join oi.menuItem m
        join oi.order o
        where o.restaurant.id = :restaurantId
          and o.status != 'CANCELLED'
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
        group by m.id, m.name
        order by sum(oi.quantity) desc
    """)
    List<TopItemDto> findTopItemsByQuantity(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );

    /**
     * Get top menu items by revenue (excluding CANCELLED orders).
     */
    @Query("""
        select new com.restaurantqr.owner.TopItemDto(
            m.id,
            m.name,
            sum(oi.quantity),
            coalesce(sum(oi.totalPrice), 0)
        )
        from OrderItem oi
        join oi.menuItem m
        join oi.order o
        where o.restaurant.id = :restaurantId
          and o.status != 'CANCELLED'
          and o.createdAt >= :startDate
          and o.createdAt < :endDate
        group by m.id, m.name
        order by sum(oi.totalPrice) desc
    """)
    List<TopItemDto> findTopItemsByRevenue(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate,
            Pageable pageable
    );
}


