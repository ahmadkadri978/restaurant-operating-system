package com.restaurantqr.servicecall;

import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public - Service Calls", description = "Customer service call endpoints (no JWT required)")
@RestController
@RequestMapping("/api/v1/public")
public class ServiceCallPublicController {

    private static final Logger log = LoggerFactory.getLogger(ServiceCallPublicController.class);

    private final ServiceCallService serviceCallService;

    public ServiceCallPublicController(ServiceCallService serviceCallService) {
        this.serviceCallService = serviceCallService;
    }
    @Operation(
            summary = "Create a service call from a table (Call Waiter / Ask Bill)",
            description = """
                    Creates a service call for the restaurant resolved by `qrToken`.
                    
                    **Anti-spam rule**
                    - A table can submit a service call only once every configured cooldown window.
                    - If violated, API returns **429 Too Many Requests**.
                    
                    The call is considered "active" for staff for **3 minutes** (time-based, no manual status).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service call created successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ServiceCallResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or restaurant inactive",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invalid or inactive QR token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Spam blocked (cooldown not finished)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })

    @PostMapping("/tables/{qrToken}/service-calls")
    public ResponseEntity<ServiceCallResponse> createCall(
            @PathVariable String qrToken,
            @Valid @RequestBody CreateServiceCallRequest request
    ) {
        log.info("POST /public/tables/{}/service-calls", qrToken);
        ServiceCallResponse resp = serviceCallService.createCall(qrToken, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }
}

