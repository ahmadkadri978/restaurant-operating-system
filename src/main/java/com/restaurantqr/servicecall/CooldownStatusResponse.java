package com.restaurantqr.servicecall;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "CooldownStatusResponse", description = "Cooldown status for a table's service calls")
public record CooldownStatusResponse(

        @Schema(description = "Whether a cooldown is currently active", example = "true")
        boolean active,

        @Schema(description = "Remaining cooldown seconds (0 if not active)", example = "142")
        int cooldownRemainingSeconds,

        @Schema(description = "Type of the active service call (null if not active)", example = "WAITER")
        CallType callType,

        @Schema(description = "When the active service call was created (null if not active)",
                example = "2026-01-07T21:20:00.000Z")
        Instant createdAt
) {}

