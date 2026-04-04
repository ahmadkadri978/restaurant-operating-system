package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.dto.SessionBillDto;
import com.restaurantqr.table.*;
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

@RestController
public class BillController {

    private static final Logger log = LoggerFactory.getLogger(BillController.class);

    private final BillCalculationService billCalculationService;
    private final PublicSessionService publicSessionService;
    private final RestaurantTableRepository tableRepository;
    private final TableSessionRepository sessionRepository;

    public BillController(BillCalculationService billCalculationService,
                          PublicSessionService publicSessionService,
                          RestaurantTableRepository tableRepository,
                          TableSessionRepository sessionRepository) {
        this.billCalculationService = billCalculationService;
        this.publicSessionService = publicSessionService;
        this.tableRepository = tableRepository;
        this.sessionRepository = sessionRepository;
    }

    // ==================== PUBLIC ENDPOINT ====================

    @Tag(name = "Public - Bill", description = "Customer-facing session bill (no JWT required)")
    @Operation(
            summary = "Get current session bill",
            description = """
                    Returns the bill for the current active table session.
                    Includes items subtotal + all applied restaurant charges (taxes, fees).
                    
                    While the session is open, the bill is computed live (DRAFT).
                    After session close, the finalized snapshot is returned.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bill retrieved",
                    content = @Content(schema = @Schema(implementation = SessionBillDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or expired session code",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invalid QR token or no session",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/api/v1/public/tables/{qrToken}/bill")
    public ResponseEntity<SessionBillDto> getPublicBill(
            @PathVariable String qrToken,
            @RequestParam String code
    ) {
        log.info("PUBLIC_GET_BILL | qrToken={} | codeProvided={}", qrToken, code != null);

        // Verify session
        publicSessionService.verifySessionOrThrow(qrToken, code);

        // Resolve table
        RestaurantTable table = tableRepository.findActiveByQrTokenFetchRestaurant(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or inactive QR token"));

        // Find active session for this table
        TableSession session = sessionRepository.findOpenSessionByTableId(table.getId())
                .orElseThrow(() -> new BusinessRuleException("No active session for this table"));

        SessionBillDto bill = billCalculationService.getOrComputeBill(session.getId());

        log.info("PUBLIC_GET_BILL_SUCCESS | qrToken={} | grandTotal={}", qrToken, bill.grandTotal());
        return ResponseEntity.ok(bill);
    }

    // ==================== STAFF ENDPOINT ====================

    @Tag(name = "Staff - Bill", description = "Staff session bill view (JWT required)")
    @Operation(
            summary = "Get session bill for a table",
            description = """
                    Returns the bill for the current or most recent session of a table.
                    Staff can preview the bill before closing the session.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bill retrieved",
                    content = @Content(schema = @Schema(implementation = SessionBillDto.class))),
            @ApiResponse(responseCode = "404", description = "Table or session not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasAnyRole('MANAGER', 'STAFF', 'OWNER')")
    @GetMapping("/api/v1/staff/tables/{tableId}/bill")
    public ResponseEntity<SessionBillDto> getStaffBill(
            Authentication authentication,
            @PathVariable Long tableId
    ) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Long restaurantId = principal.restaurantId();

        log.info("STAFF_GET_BILL | restaurantId={} | tableId={}", restaurantId, tableId);

        // Find active session for this table
        TableSession session = sessionRepository.findOpenSessionByTableId(tableId)
                .orElseThrow(() -> new BusinessRuleException("No active session for table: " + tableId));

        // Restaurant boundary check
        if (!session.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        SessionBillDto bill = billCalculationService.getOrComputeBill(session.getId());

        log.info("STAFF_GET_BILL_SUCCESS | restaurantId={} | tableId={} | grandTotal={}",
                restaurantId, tableId, bill.grandTotal());
        return ResponseEntity.ok(bill);
    }
}

