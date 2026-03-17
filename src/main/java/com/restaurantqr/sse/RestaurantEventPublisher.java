package com.restaurantqr.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for publishing real-time events to connected SSE clients.
 * Events are broadcast only to clients connected to the specific restaurant.
 */
@Service
public class RestaurantEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventPublisher.class);

    private final SseEmitterRegistry registry;

    public RestaurantEventPublisher(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Publish an ORDER_CREATED event when a new order is submitted.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the order created payload
     */
    public void publishOrderCreated(Long restaurantId, OrderCreatedPayload payload) {
        log.info("Publishing ORDER_CREATED event. restaurantId={}, orderId={}", 
                restaurantId, payload.orderId());
        
        SseEvent<OrderCreatedPayload> event = SseEvent.of(
                SseEventType.ORDER_CREATED,
                restaurantId,
                payload
        );
        registry.broadcast(restaurantId, event);
    }

    /**
     * Publish an ORDER_SENT_TO_KITCHEN event when an order is sent to kitchen.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the order status payload
     */
    public void publishOrderSentToKitchen(Long restaurantId, OrderStatusPayload payload) {
        log.info("Publishing ORDER_SENT_TO_KITCHEN event. restaurantId={}, orderId={}", 
                restaurantId, payload.orderId());
        
        SseEvent<OrderStatusPayload> event = SseEvent.of(
                SseEventType.ORDER_SENT_TO_KITCHEN,
                restaurantId,
                payload
        );
        registry.broadcast(restaurantId, event);
    }

    /**
     * Publish an ORDER_DONE event when an order is marked as done.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the order status payload
     */
    public void publishOrderDone(Long restaurantId, OrderStatusPayload payload) {
        log.info("Publishing ORDER_DONE event. restaurantId={}, orderId={}", 
                restaurantId, payload.orderId());
        
        SseEvent<OrderStatusPayload> event = SseEvent.of(
                SseEventType.ORDER_DONE,
                restaurantId,
                payload
        );
        registry.broadcast(restaurantId, event);
    }

    /**
     * Publish an ORDER_CANCELLED event when an order is cancelled.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the order status payload
     */
    public void publishOrderCancelled(Long restaurantId, OrderStatusPayload payload) {
        log.info("Publishing ORDER_CANCELLED event. restaurantId={}, orderId={}", 
                restaurantId, payload.orderId());
        
        SseEvent<OrderStatusPayload> event = SseEvent.of(
                SseEventType.ORDER_CANCELLED,
                restaurantId,
                payload
        );
        registry.broadcast(restaurantId, event);
    }

    /**
     * Publish a SERVICE_CALL event when a new service call is created.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the service call payload
     */
    public void publishServiceCall(Long restaurantId, ServiceCallPayload payload) {
        log.info("Publishing SERVICE_CALL event. restaurantId={}, callId={}", 
                restaurantId, payload.callId());
        
        SseEvent<ServiceCallPayload> event = SseEvent.of(
                SseEventType.SERVICE_CALL,
                restaurantId,
                payload
        );
        registry.broadcast(restaurantId, event);
    }
}

