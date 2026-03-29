package com.restaurantqr.order.staff;


import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.order.CustomerOrder;
import com.restaurantqr.order.CustomerOrderRepository;
import com.restaurantqr.order.OrderItemRepository;
import com.restaurantqr.sse.OrderStatusPayload;
import com.restaurantqr.sse.SseEventType;
import com.restaurantqr.sse.SseOutboundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StaffOrderService {

    private static final Logger log = LoggerFactory.getLogger(StaffOrderService.class);

    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;

    public StaffOrderService(CustomerOrderRepository orderRepository,
                             OrderItemRepository orderItemRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderDetails(Long restaurantId, Long orderId) {
        if (restaurantId == null || orderId == null) {
            throw new BusinessRuleException("restaurantId and orderId are required");
        }

        log.info("Get order details | restaurantId={} | orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findByIdAndRestaurantIdFetchTable(restaurantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        var items = orderItemRepository.findByOrderIdWithMenuItem(orderId).stream()
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
                order.getRestaurantOrderNumber(),
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
        if (restaurantId == null || orderId == null) {
            throw new BusinessRuleException("restaurantId and orderId are required");
        }

        log.info("Mark SENT_TO_KITCHEN | restaurantId={} | orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findByIdAndRestaurantIdForUpdate(restaurantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Idempotent
        if (order.getStatus() == OrderStatus.SENT_TO_KITCHEN) {
            return new OrderStatusResponse(order.getId(), order.getRestaurantOrderNumber(), order.getStatus(), order.getSentToKitchenAt(), null, null);
        }

        // Optional stricter rule: only NEW -> SENT_TO_KITCHEN
        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessRuleException("Only NEW orders can be sent to kitchen");
        }

        order.setStatus(OrderStatus.SENT_TO_KITCHEN);
        order.setSentToKitchenAt(Instant.now());

        // save() optional because managed+transactional, but keep explicit for clarity
        orderRepository.save(order);

        // Publish SSE event after transaction commits
        eventPublisher.publishEvent(SseOutboundEvent.of(
                SseEventType.ORDER_SENT_TO_KITCHEN,
                restaurantId,
                new OrderStatusPayload(
                        order.getId(),
                        order.getRestaurantOrderNumber(),
                        order.getTable().getTableNumber(),
                        order.getStatus(),
                        order.getSentToKitchenAt(),
                        null,
                        null
                )
        ));

        return new OrderStatusResponse(order.getId(), order.getRestaurantOrderNumber(), order.getStatus(), order.getSentToKitchenAt(), null, null);
    }

    @Transactional
    public OrderStatusResponse markDone(Long restaurantId, Long orderId) {
        if (restaurantId == null || orderId == null) {
            throw new BusinessRuleException("restaurantId and orderId are required");
        }

        log.info("Mark DONE | restaurantId={} | orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findByIdAndRestaurantIdForUpdate(restaurantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.DONE) {
            return new OrderStatusResponse(
                    order.getId(),
                    order.getRestaurantOrderNumber(),
                    order.getStatus(),
                    order.getSentToKitchenAt(),
                    order.getDoneAt(),
                    order.getCancelledAt()
            );
        }

        if (order.getStatus() != OrderStatus.SENT_TO_KITCHEN) {
            throw new BusinessRuleException("Order must be SENT_TO_KITCHEN before marking DONE");
        }

        order.setStatus(OrderStatus.DONE);
        order.setDoneAt(Instant.now());

        orderRepository.save(order);

        // Publish SSE event after transaction commits
        eventPublisher.publishEvent(SseOutboundEvent.of(
                SseEventType.ORDER_DONE,
                restaurantId,
                new OrderStatusPayload(
                        order.getId(),
                        order.getRestaurantOrderNumber(),
                        order.getTable().getTableNumber(),
                        order.getStatus(),
                        order.getSentToKitchenAt(),
                        order.getDoneAt(),
                        null
                )
        ));

        return new OrderStatusResponse(
                order.getId(),
                order.getRestaurantOrderNumber(),
                order.getStatus(),
                order.getSentToKitchenAt(),
                order.getDoneAt(),
                order.getCancelledAt()
        );
    }

    @Transactional
    public OrderStatusResponse markCancelled(Long restaurantId, Long orderId) {
        if (restaurantId == null || orderId == null) {
            throw new BusinessRuleException("restaurantId and orderId are required");
        }

        log.info("Mark CANCELLED | restaurantId={} | orderId={}", restaurantId, orderId);

        CustomerOrder order = orderRepository.findByIdAndRestaurantIdForUpdate(restaurantId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return new OrderStatusResponse(
                    order.getId(),
                    order.getRestaurantOrderNumber(),
                    order.getStatus(),
                    order.getSentToKitchenAt(),
                    order.getDoneAt(),
                    order.getCancelledAt()
            );
        }

        if (order.getStatus() != OrderStatus.NEW) {
            throw new BusinessRuleException("Order must be NEW before marking CANCELLED");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());

        orderRepository.save(order);

        // Publish SSE event after transaction commits
        eventPublisher.publishEvent(SseOutboundEvent.of(
                SseEventType.ORDER_CANCELLED,
                restaurantId,
                new OrderStatusPayload(
                        order.getId(),
                        order.getRestaurantOrderNumber(),
                        order.getTable().getTableNumber(),
                        order.getStatus(),
                        order.getSentToKitchenAt(),
                        order.getDoneAt(),
                        order.getCancelledAt()
                )
        ));

        return new OrderStatusResponse(
                order.getId(),
                order.getRestaurantOrderNumber(),
                order.getStatus(),
                order.getSentToKitchenAt(),
                order.getDoneAt(),
                order.getCancelledAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getOrdersByStatus(Long restaurantId,
                                                   OrderStatus status,
                                                   Pageable pageable,
                                                   Instant since) {
        if (restaurantId == null || status == null || pageable == null) {
            throw new BusinessRuleException("restaurantId, status, pageable are required");
        }

        log.debug("Get orders by status | restaurantId={} | status={} | page={} | size={} | since={}",
                restaurantId, status, pageable.getPageNumber(), pageable.getPageSize(), since);

        Page<CustomerOrder> pageResult = (since == null)
                ? orderRepository.findPageByRestaurantAndStatusFetchTable(restaurantId, status, pageable)
                : orderRepository.findPageByRestaurantAndStatusAndCreatedAtAfterFetchTable(restaurantId, status, since, pageable);

        return pageResult.map(o -> new OrderSummaryDto(
                o.getId(),
                o.getRestaurantOrderNumber(),
                o.getTable().getTableNumber(),
                o.getCreatedAt(),
                o.getTotalAmount(),
                o.getStatus()
        ));
    }
}

