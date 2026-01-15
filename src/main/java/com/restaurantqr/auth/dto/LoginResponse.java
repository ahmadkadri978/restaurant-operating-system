package com.restaurantqr.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Login response containing JWT and user context")
public record LoginResponse(
        @Schema(description = "JWT token", example = "eyJhbGciOiJIUzM4NCJ9...")
        String token,

        @Schema(description = "User role", example = "MANAGER")
        String role,

        @Schema(description = "Restaurant ID from user account", example = "1")
        Long restaurantId,

        @Schema(description = "User ID", example = "1")
        Long userId
) {}
