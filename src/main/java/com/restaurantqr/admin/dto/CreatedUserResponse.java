package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Created user information (password never returned)")
public record CreatedUserResponse(

        @Schema(description = "User ID", example = "10")
        Long userId,

        @Schema(description = "Username", example = "bella_manager")
        String username,

        @Schema(description = "User role", example = "MANAGER")
        String role
) {}

