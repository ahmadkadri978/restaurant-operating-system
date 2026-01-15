package com.restaurantqr.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Single item entry inside an order")
public record SubmitOrderItemRequest(
        @Schema(example = "1") @NotNull Long menuItemId,
        @Schema(example = "2") @NotNull @Min(1) Integer quantity,
        @Schema(example = "No ice") @Size(max = 500) String note
) {}
