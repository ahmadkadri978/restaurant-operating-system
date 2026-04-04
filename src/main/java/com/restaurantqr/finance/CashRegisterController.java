package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.finance.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@Tag(name = "Manager - Cash Register", description = "Cash register session management (open, close, movements)")
@RestController
@RequestMapping("/api/v1/manager/cash-register")
@PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
public class CashRegisterController {

    private static final Logger log = LoggerFactory.getLogger(CashRegisterController.class);

    private final CashRegisterService cashRegisterService;

    public CashRegisterController(CashRegisterService cashRegisterService) {
        this.cashRegisterService = cashRegisterService;
    }

    @Operation(
            summary = "Open cash register",
            description = "Opens a new cash register session with an opening balance. Only one register can be open per restaurant at a time.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Register opened"),
            @ApiResponse(responseCode = "400", description = "Already open or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/open")
    public ResponseEntity<CashRegisterDto> openRegister(
            Authentication authentication,
            @RequestBody OpenCashRegisterRequest request
    ) {
        AuthPrincipal principal = extractPrincipal(authentication);
        log.info("OPEN_REGISTER | restaurantId={} | userId={} | openingBalance={}",
                principal.restaurantId(), principal.userId(), request.openingBalance());

        CashRegisterDto result = cashRegisterService.openRegister(
                principal.restaurantId(), principal.userId(), request.openingBalance());

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Get current open register",
            description = "Returns the currently open register session with live running totals, or empty if none is open.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @GetMapping("/current")
    public ResponseEntity<CashRegisterDto> getCurrentRegister(Authentication authentication) {
        AuthPrincipal principal = extractPrincipal(authentication);

        return cashRegisterService.getCurrentRegister(principal.restaurantId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.ok().build());
    }

    @Operation(
            summary = "Record cash movement",
            description = "Records a cash-in or cash-out movement on the currently open register.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Movement recorded"),
            @ApiResponse(responseCode = "400", description = "No open register or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/movement")
    public ResponseEntity<CashMovementDto> recordMovement(
            Authentication authentication,
            @RequestBody CashMovementRequest request
    ) {
        AuthPrincipal principal = extractPrincipal(authentication);
        log.info("RECORD_MOVEMENT | restaurantId={} | type={} | amount={} | reason={}",
                principal.restaurantId(), request.type(), request.amount(), request.reason());

        CashMovementDto result = cashRegisterService.recordMovement(
                principal.restaurantId(), principal.userId(),
                request.type(), request.amount(), request.reason());

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Close cash register",
            description = "Closes the current register session. Requires the actual cash count. Returns expected vs actual with discrepancy.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Register closed with reconciliation"),
            @ApiResponse(responseCode = "400", description = "No open register or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/close")
    public ResponseEntity<CashRegisterDto> closeRegister(
            Authentication authentication,
            @RequestBody CloseCashRegisterRequest request
    ) {
        AuthPrincipal principal = extractPrincipal(authentication);
        log.info("CLOSE_REGISTER | restaurantId={} | userId={} | actualClosing={}",
                principal.restaurantId(), principal.userId(), request.actualClosing());

        CashRegisterDto result = cashRegisterService.closeRegister(
                principal.restaurantId(), principal.userId(),
                request.actualClosing(), request.notes());

        return ResponseEntity.ok(result);
    }

    // ==================== Private helpers ====================

    private AuthPrincipal extractPrincipal(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("Authentication principal is required");
        }
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        if (principal.restaurantId() == null) {
            throw new IllegalStateException("Restaurant ID not found in authentication principal");
        }
        return principal;
    }
}

