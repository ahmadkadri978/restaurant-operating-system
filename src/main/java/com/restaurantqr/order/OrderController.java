package com.restaurantqr.order;

import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public - Orders", description = "Customer order endpoints (no JWT required)")
@RestController
@RequestMapping("/api/v1/public")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    @Operation(
            summary = "Submit order for a table",
            description = """
                    Converts client-side cart into a stored order.
                    
                    **Business rules**
                    - One order per minute per table (429 if violated)
                    - Prices are calculated server-side from DB (frontend must not send prices)
                    
                    **Concurrency safety**
                    - Table row is locked using pessimistic locking to prevent race conditions.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SubmitOrderResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invalid QR token / Item not found",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation or business rule violation",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })

    @PostMapping("/tables/{qrToken}/orders")
    public ResponseEntity<SubmitOrderResponse> submitOrder(
            @PathVariable String qrToken,
            @Valid @RequestBody SubmitOrderRequest request
    ) {
        log.info("POST /public/tables/{}/orders", qrToken);
        SubmitOrderResponse resp = orderService.submitOrder(qrToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}
