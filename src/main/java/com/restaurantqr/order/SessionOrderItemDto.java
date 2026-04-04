package com.restaurantqr.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Single item within a session order (customer-facing)")
public record SessionOrderItemDto(
        @Schema(description = "Menu item name", example = "Cola") String itemName,
        @Schema(description = "Quantity ordered", example = "2") Integer quantity,
        @Schema(description = "Unit price", example = "2.50") BigDecimal unitPrice,
        @Schema(description = "Line total = unitPrice × quantity", example = "5.00") BigDecimal totalPrice,
        @Schema(description = "Item note from customer", example = "No ice") String note
) {}

