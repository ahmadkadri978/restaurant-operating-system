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
    
    // Connection events
    HEARTBEAT,
    CONNECTED
}

