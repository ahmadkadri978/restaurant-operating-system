package com.restaurantqr.servicecall;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ServiceCallResponse", description = "Service call notification returned by the API")
public record ServiceCallResponse(
        @Schema(description = "Service call ID", example = "55")
        Long id,

        @Schema(description = "Table number", example = "5")
        Integer tableNumber,

        @Schema(description = "Call type", example = "WAITER")
        CallType callType,

        @Schema(description = "Creation timestamp", example = "2026-01-07T21:20:00.000Z")
        Instant createdAt
) {}
