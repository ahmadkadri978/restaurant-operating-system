package com.restaurantqr.sse;

import com.restaurantqr.servicecall.CallType;

import java.time.Instant;

/**
 * Payload for SERVICE_CALL events.
 */
public record ServiceCallPayload(
        Long callId,
        int tableNumber,
        CallType callType,
        Instant createdAt
) {}


