package com.restaurantqr.order.staff;


import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import org.slf4j.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class StaffOrderService {

    private static final Logger log = LoggerFactory.getLogger(StaffOrderService.class);

    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public StaffOrderService(CustomerOrderRepository orderRepository,
                             OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getRecentOrders(Long restaurantId, Pageable pageable, Instant since) {
        log.info("Get recent orders. restaurantId={}, page={}, size={}, since={}",
                restaurantId, pageable.getPageNumber(), pageable.getPageSize(),since);

        // Polling mode
        Page<CustomerOrder> pageResult = (since == null)
                ? orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)
                : orderRepository.findNewOrdersSince(restaurantId, since, pageable);

        return pageResult.map(o -> new OrderSummaryDto(
                o.getId(),
                o.getTable().getTableNumber(),
                o.getCreatedAt(),
                o.getTotalAmount(),
                o.getStatus()
        ));
    }

    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderDetails(Long restaurantId, Long orderId) {
        log.info("Get order details. restaurantId={}, orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // ✅ Security boundary: order must belong to same restaurant
        if (!order.getRestaurant().getId().equals(restaurantId)) {
            log.warn("Order access denied by restaurant boundary. orderId={}, requestedRestaurantId={}, actualRestaurantId={}",
                    orderId, restaurantId, order.getRestaurant().getId());
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        var items = orderItemRepository.findByOrderId(orderId).stream()
                .map(oi -> new OrderItemDto(
                        oi.getMenuItem().getId(),
                        oi.getMenuItem().getName(),
                        oi.getQuantity(),
                        oi.getUnitPrice(),
                        oi.getTotalPrice(),
                        oi.getNote()

                ))
                .toList();

        return new OrderDetailsDto(
                order.getId(),
                order.getTable().getTableNumber(),
                order.getCreatedAt(),
                order.getTotalAmount(),
                order.getNote(),
                items,
                order.getStatus()
        );
    }
    @Transactional
    public OrderStatusResponse markSentToKitchen(Long restaurantId, Long orderId) {
        log.info("Mark order as SENT_TO_KITCHEN. restaurantId={}, orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Restaurant boundary
        if (!order.getRestaurant().getId().equals(restaurantId)) {
            log.warn("Order not found under restaurant boundary. orderId={}, requestedRestaurantId={}, actualRestaurantId={}",
                    orderId, restaurantId, order.getRestaurant().getId());
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        if (order.getStatus() == OrderStatus.SENT_TO_KITCHEN) {
            log.info("Order already SENT_TO_KITCHEN. orderId={}", orderId);
            return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSentToKitchenAt());
        }

        order.setStatus(OrderStatus.SENT_TO_KITCHEN);
        order.setSentToKitchenAt(Instant.now());

        // save not required if managed entity, but explicit is OK
        orderRepository.save(order);

        log.info("Order marked as SENT_TO_KITCHEN. orderId={}", orderId);
        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSentToKitchenAt());
    }

}

