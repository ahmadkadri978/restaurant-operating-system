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
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@Tag(name = "Staff - Orders", description = "Staff dashboard endpoints (JWT required)")
@RestController
@RequestMapping("/api/v1/manager")
@PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
public class StaffOrderController {

    private final StaffOrderService staffOrderService;

    public StaffOrderController(StaffOrderService staffOrderService) {

        this.staffOrderService = staffOrderService;
    }
    @Operation(
            summary = "Get NEW orders (needs to be sent to kitchen) - paging + polling",
            description = """
                Returns orders with status NEW for the authenticated staff restaurant.

                **Use case**
                - Staff screen #1: orders that still need to be sent to kitchen.

                **Polling**
                - Optional `since` returns only NEW orders created after timestamp.
                - Recommended polling interval: every 2–3 seconds.
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
    @GetMapping("/orders/new")
    public Page<OrderSummaryDto> getNewOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Instant since
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return staffOrderService.getOrdersByStatus(principal.restaurantId(), OrderStatus.NEW, pageable, since);
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


    @PatchMapping("/orders/{orderId}/sent-to-kitchen")
    public OrderStatusResponse markSentToKitchen(Authentication authentication, @PathVariable Long orderId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return staffOrderService.markSentToKitchen(principal.restaurantId(), orderId);
    }

    @PatchMapping("/orders/{orderId}/cancel")
    public OrderStatusResponse cancelOrder(Authentication authentication, @PathVariable Long orderId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return staffOrderService.markCancelled(principal.restaurantId(), orderId);
    }


}

