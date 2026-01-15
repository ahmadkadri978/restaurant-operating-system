package com.restaurantqr.servicecall;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreateServiceCallRequest", description = "Request to create a service call from a table")
public record CreateServiceCallRequest(
        @Schema(
                description = "Type of service call requested",
                example = "WAITER",
                allowableValues = {"WAITER", "BILL", "NARA"}
        )
        @NotNull CallType callType
) {}