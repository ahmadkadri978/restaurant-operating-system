package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.finance.dto.CashRegisterDto;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.List;

@Tag(name = "Owner - Cash Register History", description = "Cash register history and reconciliation reports")
@RestController
@RequestMapping("/api/v1/owner/cash-register")
@PreAuthorize("hasRole('OWNER')")
public class CashRegisterOwnerController {

    private final CashRegisterService cashRegisterService;

    public CashRegisterOwnerController(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    @Operation(
            summary = "Get cash register history",
            description = "Returns all closed register sessions for the restaurant, ordered by most recent first.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @GetMapping("/history")
    public ResponseEntity<List<CashRegisterDto>> getHistory(Authentication authentication) {
        Long restaurantId = extractRestaurantId(authentication);
        List<CashRegisterDto> history = cashRegisterService.getRegisterHistory(restaurantId);
        return ResponseEntity.ok(history);
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

