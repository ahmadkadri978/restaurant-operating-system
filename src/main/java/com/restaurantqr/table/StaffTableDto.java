package com.restaurantqr.table;

import java.time.Instant;

public record StaffTableDto(
        Long id,
        Integer tableNumber,
        boolean isActive,
        String sessionCode,
        Instant sessionOpenedAt
) {}

