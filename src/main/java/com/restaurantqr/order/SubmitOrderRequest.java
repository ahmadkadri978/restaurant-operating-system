package com.restaurantqr.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Submit order request. Prices are calculated server-side from DB.")
public record SubmitOrderRequest(
        @Schema(description = "Order items (must not be empty)")
        @NotEmpty @Valid List<SubmitOrderItemRequest> items,

        @Schema(example = "Please bring napkins") @Size(max = 100) String note
) {}
