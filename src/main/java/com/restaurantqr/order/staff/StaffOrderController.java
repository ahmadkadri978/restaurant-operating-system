package com.restaurantqr.order.staff;




import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Tag(name = "Staff - Orders", description = "Staff dashboard endpoints (JWT required)")
@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasRole('STAFF')")
public class StaffOrderController {

    private final StaffOrderService staffOrderService;

    public StaffOrderController(StaffOrderService staffOrderService) {

        this.staffOrderService = staffOrderService;
    }
    @Operation(
            summary = "Get staff orders (paging + polling supported)",
            description = """
                Returns orders for the authenticated staff restaurant.
                
                **Paging**
                - Use `page` and `size` to paginate orders (sorted by `createdAt` DESC).
                
                **Polling (near real-time)**
                - If `since` is provided, the API returns only orders created after that timestamp.
                - Recommended polling interval: every 2–3 seconds.
                
                **Restaurant isolation**
                - Orders are restricted to the restaurantId inside the JWT token.
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

    @GetMapping("/orders")
    public Page<OrderSummaryDto> getOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(
                    description = """
                        Optional polling parameter.
                        If provided, only orders created after this timestamp will be returned.
                        Format: ISO-8601 (e.g. 2026-01-07T21:15:30Z)
                        """,
                    example = "2026-01-07T21:15:30Z"
            )
            @RequestParam(required = false) Instant since

    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return staffOrderService.getRecentOrders(principal.restaurantId(), pageable, since);
    }
    @Operation(
            summary = "Get order details by id",
            description = """
                    Returns order details + items.
                    Access is restricted to the staff's restaurant only.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order details returned",
                    content = @Content(schema = @Schema(implementation = OrderDetailsDto.class))),
            @ApiResponse(responseCode = "404", description = "Order not found (or belongs to another restaurant)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })

    @GetMapping("/orders/{orderId}")
    public OrderDetailsDto getOrderDetails(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return staffOrderService.getOrderDetails(principal.restaurantId(), orderId);
    }
    @Operation(
            summary = "Mark an order as sent to kitchen",
            description = """
                    Changes order status from NEW -> SENT_TO_KITCHEN.
                    
                    Purpose: Frontend can color-code orders:
                    - NEW: needs to be sent to kitchen
                    - SENT_TO_KITCHEN: already in progress
                    
                    Notes:
                    - Idempotent: if already SENT_TO_KITCHEN, returns 200 with current state.
                    - Restaurant isolation enforced using restaurantId from JWT.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order status updated",
                    content = @Content(schema = @Schema(implementation = OrderStatusResponse.class))),
            @ApiResponse(responseCode = "401", ref = "Unauthorized"),
            @ApiResponse(responseCode = "403", ref = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Order not found (or belongs to another restaurant)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PatchMapping("/orders/{orderId}/sent-to-kitchen")
    public OrderStatusResponse markSentToKitchen(Authentication authentication, @PathVariable Long orderId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return staffOrderService.markSentToKitchen(principal.restaurantId(), orderId);
    }
}

