package com.restaurantqr.auth.dto;

public record AuthPrincipal(
        Long userId,
        String role,
        Long restaurantId
) {}
