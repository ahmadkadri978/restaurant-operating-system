package com.restaurantqr.servicecall;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@Tag(name = "Staff - Service Calls", description = "Staff dashboard endpoints (JWT required)")
@RestController
@RequestMapping("/api/v1/staff")
public class ServiceCallStaffController {

    private final ServiceCallService serviceCallService;

    public ServiceCallStaffController(ServiceCallService serviceCallService) {
        this.serviceCallService = serviceCallService;
    }
    @Operation(
            summary = "Get active service calls (polling supported)",
            description = """
                Returns active service calls for the authenticated staff restaurant.

                **Active definition**
                - A service call is active if `createdAt` is within the last **3 minutes**.
                - Calls disappear automatically after that time (no status workflow).

                **Polling (near real-time)**
                - If `since` is provided, only service calls created after that timestamp are returned.
                - Recommended polling interval: every 2 seconds.

                **Restaurant isolation**
                - Enforced using `restaurantId` claim in JWT.
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of active service calls",
                    content = @Content(mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = ServiceCallResponse.class)))),

            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),

            @ApiResponse(responseCode = "403", description = "Forbidden (wrong role)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/service-calls")
    public List<ServiceCallResponse> getActiveCalls(
            Authentication authentication,

            @Parameter(
                    description = """
                        Optional polling parameter.
                        If provided, only service calls created after this timestamp are returned.
                        Format: ISO-8601 (e.g. 2026-01-07T21:15:30Z)
                        """,
                    example = "2026-01-07T21:15:30Z"
            )
            @RequestParam(required = false) Instant since
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        return serviceCallService.getActiveCalls(principal.restaurantId(), since);
    }

}
