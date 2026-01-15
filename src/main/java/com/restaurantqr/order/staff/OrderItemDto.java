package com.restaurantqr.order.staff;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "OrderItemDto", description = "Single item within an order")
public record OrderItemDto(
        @Schema(description = "Menu item ID", example = "100")
        Long menuItemId,

        @Schema(description = "Menu item name snapshot at time of order", example = "Cola")
        String itemName,

        @Schema(description = "Quantity ordered", example = "2")
        Integer quantity,

        @Schema(description = "Unit price snapshot (server-side)", example = "2.50")
        BigDecimal unitPrice,

        @Schema(description = "Line total = unitPrice * quantity", example = "5.00")
        BigDecimal totalPrice,

        @Schema(description = "Item note from customer", example = "No ice")
        String note
) {}