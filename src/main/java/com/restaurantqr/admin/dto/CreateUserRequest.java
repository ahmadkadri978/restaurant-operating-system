package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "User credentials to create")
public record CreateUserRequest(

        @Schema(description = "Username (must be unique system-wide)", example = "bella_manager")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-z0-9_]+$", message = "Username must contain only lowercase letters, numbers and underscores")
        String username,

        @Schema(description = "Password", example = "securePass123")
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password
) {}

