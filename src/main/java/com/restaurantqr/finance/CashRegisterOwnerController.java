package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.finance.dto.CashRegisterDto;
import com.restaurantqr.finance.dto.CashRegisterPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Owner - Cash Register History", description = "Cash register history and reconciliation reports")
@RestController
@RequestMapping("/api/v1/owner/cash-register")
@PreAuthorize("hasRole('OWNER')")
public class CashRegisterOwnerController {

    private static final int MAX_PAGE_SIZE = 50;

    private final CashRegisterService cashRegisterService;

    public CashRegisterOwnerController(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    @Operation(
            summary = "Get paginated cash register history",
            description = "Returns a page of closed register sessions for the restaurant, ordered by most recent first.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated register history")
    })
    @GetMapping("/history")
    public ResponseEntity<CashRegisterPageResponse> getHistory(
            Authentication authentication,
            @Parameter(description = "0-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (1–50)", example = "10")
            @RequestParam(defaultValue = "10") int size
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        int clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        CashRegisterPageResponse response = cashRegisterService.getRegisterHistoryPaged(restaurantId, safePage, clampedSize);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Get register session details",
            description = "Returns detailed information for a specific register session including all movements.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Register session details"),
            @ApiResponse(responseCode = "404", description = "Register session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<CashRegisterDto> getRegisterById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        Long restaurantId = extractRestaurantId(authentication);
        CashRegisterDto register = cashRegisterService.getRegisterById(restaurantId, id);
        return ResponseEntity.ok(register);
    }

    // ==================== Private helpers ====================

    private Long extractRestaurantId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication principal is required");
        }
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (principal.restaurantId() == null) {
            throw new IllegalStateException("Restaurant ID not found in authentication principal");
        }
        return principal.restaurantId();
    }
}

