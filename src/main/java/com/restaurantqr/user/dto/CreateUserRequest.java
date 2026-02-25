package com.restaurantqr.user.dto;

import com.restaurantqr.user.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Generated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a new user for a restaurant")
public record CreateUserRequest(

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 100, message = "username must be between 3 and 100 characters")
        @Schema(example = "staff1")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters")
        @Schema(example = "password123")
        String password,

        @NotNull(message = "role is required")
        @Schema(example = "STAFF", allowableValues = {"STAFF", "MANAGER", "KITCHEN"})
        Role role,

        @Schema(description = "Restaurant ID (required for SUPER_ADMIN, ignored for MANAGER)", example = "1")
        Long restaurantId
) {
    @Generated("password-masking toString")
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", password=*****, role=" + role
                + ", restaurantId=" + restaurantId + "]";
    }
}

