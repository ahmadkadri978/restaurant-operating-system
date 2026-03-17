package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User details (password never returned)")
public record UserDetailResponse(

        @Schema(description = "User ID", example = "10")
        Long userId,

        @Schema(description = "Username", example = "bella_manager")
        String username,

        @Schema(description = "User role", example = "MANAGER")
        String role,

        @Schema(description = "Whether the user is active", example = "true")
        boolean isActive
) {}

