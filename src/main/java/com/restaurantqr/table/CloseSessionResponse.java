package com.restaurantqr.table;

public record CloseSessionResponse(
        Long tableId,
        boolean closed
) {}