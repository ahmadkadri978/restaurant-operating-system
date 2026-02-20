package com.restaurantqr.table;

import java.time.Instant;

public record OpenSessionResponse(
        Long tableId,
        Integer tableNumber,
        String sessionCode,
        Instant sessionOpenedAt
) {}
