package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "User update request")
public record UpdateUserRequest(

        @Schema(description = "User ID (if updating existing user)", example = "10")
        Long userId,

        @Schema(description = "Username (must be unique system-wide)", example = "bella_manager")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-z0-9_]+$", message = "Username must contain only lowercase letters, numbers and underscores")
        String username,

        @Schema(description = "Password (leave null to keep existing password when updating)", example = "newSecurePass123")
        @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
        String password,

        @Schema(description = "User role", example = "MANAGER")
        @NotNull(message = "Role is required")
        String role,

        @Schema(description = "Whether the user is active", example = "true")
        Boolean isActive
) {
    public boolean isActiveOrDefault() {
        return isActive == null || isActive;
    }
}

