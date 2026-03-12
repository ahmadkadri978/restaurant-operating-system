package com.restaurantqr.owner;

import com.restaurantqr.servicecall.CallType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ServiceCallTypeCountDto", description = "Service call count by type")
public record ServiceCallTypeCountDto(
        @Schema(description = "Call type", example = "WAITER")
        CallType callType,

        @Schema(description = "Number of calls of this type", example = "18")
        Long count
) {}

