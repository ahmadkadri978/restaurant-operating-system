package com.restaurantqr.owner;

import com.restaurantqr.order.staff.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StatusCountDto", description = "Order count by status")
public record StatusCountDto(
        @Schema(description = "Order status", example = "DONE")
        OrderStatus status,

        @Schema(description = "Number of orders with this status", example = "120")
        Long count
) {}

