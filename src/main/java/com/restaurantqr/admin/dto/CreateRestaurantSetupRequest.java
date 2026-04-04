package com.restaurantqr.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@Schema(description = "Request to create a complete restaurant setup including categories, items, tables, and users")
public record CreateRestaurantSetupRequest(

        @Schema(description = "Restaurant name", example = "Bella Italia")
        @NotBlank(message = "Restaurant name is required")
        @Size(min = 2, max = 100, message = "Restaurant name must be between 2 and 100 characters")
        String restaurantName,

        @Schema(description = "Unique restaurant code", example = "BELLA-001")
        @NotBlank(message = "Restaurant code is required")
        @Size(min = 2, max = 20, message = "Restaurant code must be between 2 and 20 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Restaurant code must contain only uppercase letters, numbers, underscores and hyphens")
        String restaurantCode,

        @Schema(description = "Menu categories with items")
        @NotEmpty(message = "At least one category is required")
        @Size(max = 50, message = "Maximum 50 categories allowed")
        @Valid
        List<CreateCategoryRequest> categories,

        @Schema(description = "Restaurant tables")
        @NotEmpty(message = "At least one table is required")
        @Size(max = 100, message = "Maximum 100 tables allowed")
        @Valid
        List<CreateTableRequest> tables,

        @Schema(description = "Users to create for this restaurant")
        @NotNull(message = "Users configuration is required")
        @Valid
        CreateUsersRequest users
) {}

