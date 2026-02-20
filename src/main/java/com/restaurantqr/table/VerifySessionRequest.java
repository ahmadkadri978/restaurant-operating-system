package com.restaurantqr.table;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifySessionRequest(
        @NotBlank
        @Pattern(regexp = "\\d{4}", message = "Code must be exactly 4 digits")
        String code
) {}