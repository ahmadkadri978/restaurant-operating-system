package com.restaurantqr.order.staff;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/kitchen")
@PreAuthorize("""
  hasAnyAuthority('MANAGER','KITCHEN','ROLE_MANAGER','ROLE_KITCHEN')
""")

public class KitchenOrderController {
    private final StaffOrderService staffOrderService;

    public KitchenOrderController(StaffOrderService staffOrderService) {

        this.staffOrderService = staffOrderService;
    }

    @Operation(
            summary = "Mark an order as DONE",
            description = """
                Changes order status from SENT_TO_KITCHEN -> DONE.

                Purpose:
                - Frontend kitchen screen can remove the order after it's completed.

                Notes:
                - Idempotent: if already DONE, returns 200 with current state.
                - Restaurant isolation enforced using restaurantId from JWT.
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked as DONE",
                    content = @Content(schema = @Schema(implementation = OrderStatusResponse.class))),
            @ApiResponse(responseCode = "400", description = "Business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", ref = "Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/orders/{orderId}/done")
    public OrderStatusResponse markDone(Authentication authentication, @PathVariable Long orderId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return staffOrderService.markDone(principal.restaurantId(), orderId);
    }


    @Operation(
            summary = "Get SENT_TO_KITCHEN orders (in progress) - paging + polling",
            description = """
                Returns orders with status SENT_TO_KITCHEN for the authenticated staff restaurant.

                **Use case**
                - Staff screen #2: orders already sent to kitchen (in progress).

                **Polling**
                - Optional `since` returns only SENT_TO_KITCHEN orders created after timestamp.
                - Recommended polling interval: every 3 seconds.
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders page returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden (wrong role)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/orders/sent-to-kitchen")
    public Page<OrderSummaryDto> getSentToKitchenOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Instant since
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return staffOrderService.getOrdersByStatus(principal.restaurantId(), OrderStatus.SENT_TO_KITCHEN, pageable, since);
    }

}
