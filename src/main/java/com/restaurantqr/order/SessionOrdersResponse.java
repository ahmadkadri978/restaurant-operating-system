package com.restaurantqr.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "All orders belonging to the current active table session")
public record SessionOrdersResponse(
        @Schema(description = "Orders in this session, ordered by creation time ascending")
        List<SessionOrderDto> orders
) {}

