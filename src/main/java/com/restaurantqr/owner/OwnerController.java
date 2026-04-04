package com.restaurantqr.owner;

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
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "Owner - Dashboard", description = "Owner dashboard APIs (JWT required)")
@RestController
@RequestMapping("/api/v1/owner")
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {

    private final OwnerDashboardService dashboardService;

    public OwnerController(OwnerDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(
            summary = "Get owner dashboard statistics",
            description = """
                Returns comprehensive dashboard data for the owner including:
                - Overview statistics (total orders, revenue, etc.)
                - Revenue trend over time
                - Orders by status
                - Top selling items by quantity and revenue
                - Top tables by order count
                - Service calls by type
                - Peak hours analysis
                
                **Security:**
                - restaurantId is taken from authenticated JWT principal only
                - Never accepts restaurantId from request parameters
                
                **Parameters:**
                - period: Time period for aggregation (DAY, WEEK, MONTH, YEAR)
                - anchorDate: Reference date (defaults to today)
                - zone: Timezone for date calculations (defaults to Europe/Berlin)
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Dashboard data returned successfully",
                    content = @Content(schema = @Schema(implementation = OwnerDashboardResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden (not OWNER role)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))
            )
    })
    @GetMapping("/dashboard")
    public OwnerDashboardResponse getDashboard(
            Authentication authentication,

            @Parameter(
                    description = "Dashboard period type",
                    example = "DAY",
                    required = true
            )
            @RequestParam(defaultValue = "DAY")
            @NotNull
            DashboardPeriod period,

            @Parameter(
                    description = "Anchor date for the period (ISO format: yyyy-MM-dd)",
                    example = "2026-03-12"
            )
            @RequestParam(required = false)
            LocalDate anchorDate,

            @Parameter(
                    description = "Timezone for date calculations",
                    example = "Asia/Damascus"
            )
            @RequestParam(defaultValue = "Asia/Damascus")
            String zone
    ) {
        // Defensive null checks (should never happen with @PreAuthorize, but safety first)
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication principal is required");
        }

        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        // Validate principal has restaurantId
        if (principal.restaurantId() == null) {
            throw new IllegalStateException("Restaurant ID not found in authentication principal");
        }

        // Service call with restaurantId from JWT principal only
        // Service handles null anchorDate and zone validation
        return dashboardService.getDashboard(
                principal.restaurantId(),
                period,
                anchorDate,
                zone
        );
    }
}

