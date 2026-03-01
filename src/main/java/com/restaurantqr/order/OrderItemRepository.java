package com.restaurantqr.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
