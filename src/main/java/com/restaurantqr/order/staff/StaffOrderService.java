package com.restaurantqr.order.staff;


import com.restaurantqr.exception.BusinessRuleException;
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
            return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSentToKitchenAt(),null);
        }

        order.setStatus(OrderStatus.SENT_TO_KITCHEN);
        order.setSentToKitchenAt(Instant.now());

        // save not required if managed entity, but explicit is OK
        orderRepository.save(order);

        log.info("Order marked as SENT_TO_KITCHEN. orderId={}", orderId);
        return new OrderStatusResponse(order.getId(), order.getStatus(), order.getSentToKitchenAt(),null);
    }
    @Transactional
    public OrderStatusResponse markDone(Long restaurantId, Long orderId) {

        log.info("Mark order DONE. restaurantId={}, orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Restaurant boundary
        if (!order.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        // Idempotent
        if (order.getStatus() == OrderStatus.DONE) {
            return new OrderStatusResponse(
                    order.getId(),
                    order.getStatus(),
                    order.getSentToKitchenAt(),
                    order.getDoneAt()
            );
        }

        // Business rule (recommended)
        // We usually allow DONE only if it was sent to kitchen first.
        if (order.getStatus() != OrderStatus.SENT_TO_KITCHEN) {
            throw new BusinessRuleException("Order must be SENT_TO_KITCHEN before marking DONE");
        }

        order.setStatus(OrderStatus.DONE);
        order.setDoneAt(Instant.now());

        orderRepository.save(order);

        return new OrderStatusResponse(
                order.getId(),
                order.getStatus(),
                order.getSentToKitchenAt(),
                order.getDoneAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getOrdersByStatus(Long restaurantId,
                                                   OrderStatus status,
                                                   Pageable pageable,
                                                   Instant since) {

        log.info("Get orders by status. restaurantId={}, status={}, page={}, size={}, since={}",
                restaurantId, status, pageable.getPageNumber(), pageable.getPageSize(), since);

        Page<CustomerOrder> pageResult;

        if (since == null) {
            pageResult = orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(
                    restaurantId, status, pageable
            );
        } else {
            // option 1: Page
            pageResult = orderRepository.findByRestaurantIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                    restaurantId, status, since, pageable
            );
        }

        return pageResult.map(o -> new OrderSummaryDto(
                o.getId(),
                o.getTable().getTableNumber(),
                o.getCreatedAt(),
                o.getTotalAmount(),
                o.getStatus()
        ));
    }


}

