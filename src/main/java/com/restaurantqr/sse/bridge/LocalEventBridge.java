package com.restaurantqr.sse.bridge;

import com.restaurantqr.sse.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Fallback event bridge used when Redis is not configured.
 * Delegates directly to {@link RestaurantEventPublisher} for local-only broadcast.
 * <p>
 * This preserves the exact single-instance behavior from before Phase B.
 */
@Component
@ConditionalOnMissingBean(RedisEventBridge.class)
public class LocalEventBridge implements SseEventBridge {

    private static final Logger log = LoggerFactory.getLogger(LocalEventBridge.class);

    private final RestaurantEventPublisher eventPublisher;

    public LocalEventBridge(RestaurantEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        log.info("LocalEventBridge activated — Redis not configured, SSE events will broadcast locally only");
    }

    @Override
    public void publish(SseOutboundEvent event) {
        try {
            switch (event.eventType()) {
                case ORDER_CREATED ->
                    eventPublisher.publishOrderCreated(event.restaurantId(), (OrderCreatedPayload) event.payload());
                case ORDER_SENT_TO_KITCHEN ->
                    eventPublisher.publishOrderSentToKitchen(event.restaurantId(), (OrderStatusPayload) event.payload());
                case ORDER_DONE ->
                    eventPublisher.publishOrderDone(event.restaurantId(), (OrderStatusPayload) event.payload());
                case ORDER_CANCELLED ->
                    eventPublisher.publishOrderCancelled(event.restaurantId(), (OrderStatusPayload) event.payload());
                case SERVICE_CALL ->
                    eventPublisher.publishServiceCall(event.restaurantId(), (ServiceCallPayload) event.payload());
                case MENU_ITEM_UPDATED ->
                    eventPublisher.publishMenuItemUpdated(event.restaurantId(), (MenuItemUpdatedPayload) event.payload());
                case MENU_ITEM_DELETED ->
                    eventPublisher.publishMenuItemDeleted(event.restaurantId(), (MenuItemDeletedPayload) event.payload());
                default ->
                    log.warn("Unhandled SSE outbound event type: {}", event.eventType());
            }
        } catch (Exception e) {
            log.error("Failed to broadcast SSE event locally. type={}, restaurantId={}",
                    event.eventType(), event.restaurantId(), e);
        }
    }
}

