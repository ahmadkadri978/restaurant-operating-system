package com.restaurantqr.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "User response")
public record UserResponse(

        @Schema(description = "User ID", example = "1")
        Long id,

        @Schema(description = "Username", example = "staff1")
        String username,

        @Schema(description = "User role", example = "STAFF")
        String role,

        @Schema(description = "Restaurant ID", example = "1")
        Long restaurantId,

        @Schema(description = "Whether the user is active", example = "true")
        boolean active,

        @Schema(description = "Creation timestamp")
        Instant createdAt
) {}

