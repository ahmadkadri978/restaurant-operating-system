package com.restaurantqr.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for publishing real-time events to connected SSE clients.
 * Events are broadcast only to clients connected to the specific restaurant.
 */
@Service
public class RestaurantEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RestaurantEventPublisher.class);

    private final SseEmitterRegistry registry;
    private final PublicMenuSseRegistry publicMenuRegistry;
    private final String instanceId;

    public RestaurantEventPublisher(SseEmitterRegistry registry,
                                    PublicMenuSseRegistry publicMenuRegistry,
                                    @Value("${app.instance-id:unknown}") String instanceId) {
        this.registry = registry;
        this.publicMenuRegistry = publicMenuRegistry;
        this.instanceId = instanceId;
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
                payload,
                instanceId
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
                payload,
                instanceId
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
                payload,
                instanceId
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
                payload,
                instanceId
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
                payload,
                instanceId
        );
        registry.broadcast(restaurantId, event);
    }

    // ==================== Menu Events (Public SSE) ====================

    /**
     * Publish a MENU_ITEM_UPDATED event when a menu item is created or updated.
     * This is broadcast to customers via the public SSE stream.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the menu item updated payload
     */
    public void publishMenuItemUpdated(Long restaurantId, MenuItemUpdatedPayload payload) {
        log.info("Publishing MENU_ITEM_UPDATED event. restaurantId={}, itemId={}, available={}, active={}", 
                restaurantId, payload.itemId(), payload.isAvailable(), payload.isActive());
        
        SseEvent<MenuItemUpdatedPayload> event = SseEvent.of(
                SseEventType.MENU_ITEM_UPDATED,
                restaurantId,
                payload,
                instanceId
        );
        publicMenuRegistry.broadcast(restaurantId, event);
    }

    /**
     * Publish a MENU_ITEM_DELETED event when a menu item is deleted.
     * This is broadcast to customers via the public SSE stream.
     *
     * @param restaurantId the restaurant ID
     * @param payload      the menu item deleted payload
     */
    public void publishMenuItemDeleted(Long restaurantId, MenuItemDeletedPayload payload) {
        log.info("Publishing MENU_ITEM_DELETED event. restaurantId={}, itemId={}", 
                restaurantId, payload.itemId());
        
        SseEvent<MenuItemDeletedPayload> event = SseEvent.of(
                SseEventType.MENU_ITEM_DELETED,
                restaurantId,
                payload,
                instanceId
        );
        publicMenuRegistry.broadcast(restaurantId, event);
    }
}

