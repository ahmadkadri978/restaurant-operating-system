package com.restaurantqr.sse;

/**
 * Types of events that can be broadcast via SSE.
 */
public enum SseEventType {
    // Order events
    ORDER_CREATED,
    ORDER_SENT_TO_KITCHEN,
    ORDER_DONE,
    ORDER_CANCELLED,
    
    // Service call events
    SERVICE_CALL,
    
    // Menu events (for public/customer SSE)
    MENU_ITEM_UPDATED,
    MENU_ITEM_DELETED,
    
    // Connection events
    HEARTBEAT,
    CONNECTED
}

