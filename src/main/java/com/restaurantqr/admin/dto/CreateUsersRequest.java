package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Users configuration for the new restaurant")
public record CreateUsersRequest(

        @Schema(description = "Owner user (optional - has full dashboard access)")
        @Valid
        CreateUserRequest owner,

        @Schema(description = "Manager user (required)")
        @NotNull(message = "Manager user is required")
        @Valid
        CreateUserRequest manager,

        @Schema(description = "Staff user (required)")
        @NotNull(message = "Staff user is required")
        @Valid
        CreateUserRequest staff,

        @Schema(description = "Kitchen user (required)")
        @NotNull(message = "Kitchen user is required")
        @Valid
        CreateUserRequest kitchen
) {}


