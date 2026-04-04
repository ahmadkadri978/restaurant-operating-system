package com.restaurantqr.table;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.finance.BillCalculationService;
import com.restaurantqr.finance.dto.BillPreviewDto;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Staff - Tables", description = "Staff table sessions management (open/close)")
@RestController
@RequestMapping("/api/v1/manager")
@PreAuthorize("hasAnyRole('MANAGER', 'STAFF')")
public class StaffTableController {
    private static final Logger log = LoggerFactory.getLogger(StaffTableController.class);

    private final StaffTableSessionService sessionService;
    private final BillCalculationService billCalculationService;
    private final TableSessionRepository sessionRepository;

    public StaffTableController(StaffTableSessionService sessionService,
                                BillCalculationService billCalculationService,
                                TableSessionRepository sessionRepository) {
        this.sessionService = sessionService;
        this.billCalculationService = billCalculationService;
        this.sessionRepository = sessionRepository;
    }

    @Operation(
            summary = "List restaurant tables (with session code if open)",
            description = """
                    Returns all tables for the authenticated staff restaurant.
                    Includes sessionCode/sessionOpenedAt if a session is currently open.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tables returned"),
            @ApiResponse(responseCode = "401", description = "Missing/invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/tables")
    public List<StaffTableDto> listTables(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        log.info("MANAGER_LIST_TABLES | restaurantId={}", principal.restaurantId());

        List<StaffTableDto> result =
                sessionService.listTables(principal.restaurantId());

        log.info("MANAGER_LIST_TABLES_SUCCESS | restaurantId={} | tablesCount={}",
                principal.restaurantId(),
                result.size());

        return result;
    }

    @Operation(
            summary = "Open table session (generate 4-digit code)",
            description = """
                    Opens a session for a given table and generates a 4-digit code.
                    If session is already open, returns existing code (idempotent).
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session opened"),
            @ApiResponse(responseCode = "400", description = "Business rule violation",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/tables/{tableId}/session/open")
    public ResponseEntity<OpenSessionResponse> openSession(Authentication authentication, @PathVariable Long tableId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        log.info("MANAGER_OPEN_SESSION_REQUEST | restaurantId={} | tableId={}",
                principal.restaurantId(), tableId);

        OpenSessionResponse response =
                sessionService.openSession(principal.restaurantId(), tableId);

        log.info("MANAGER_OPEN_SESSION_SUCCESS | restaurantId={} | tableId={} | sessionCode={}",
                principal.restaurantId(), tableId, response.sessionCode());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Deprecated
    @Operation(
            summary = "Close table session (legacy)",
            description = """
                    Legacy endpoint: closes the session for a given table by clearing sessionCode and sessionOpenedAt.
                    Maintained for backward compatibility. New clients should use /tables/{tableId}/session/close-with-payment
                    (even for zero-total sessions with amountReceived=0, tipAmount=0), which records a SessionPayment.
                    The legacy path now also records a zero-value SessionPayment when a finalized bill exists.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session closed"),
            @ApiResponse(responseCode = "404", description = "Table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PostMapping("/tables/{tableId}/session/close")
    public CloseSessionResponse closeSession(Authentication authentication, @PathVariable Long tableId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        log.info("MANAGER_CLOSE_SESSION_REQUEST | restaurantId={} | tableId={}",
                principal.restaurantId(), tableId);

        CloseSessionResponse response =
                sessionService.closeSession(principal.restaurantId(), tableId);

        log.info("MANAGER_CLOSE_SESSION_SUCCESS | restaurantId={} | tableId={}",
                principal.restaurantId(), tableId);

        return response;
    }

    @Operation(
            summary = "Get bill preview for an open table session",
            description = """
                    Returns a rich bill preview with aggregated order items, charges breakdown,
                    and totals for the currently open session on this table.
                    Used by the frontend before closing a session to display the invoice preview.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bill preview returned",
                    content = @Content(schema = @Schema(implementation = BillPreviewDto.class))),
            @ApiResponse(responseCode = "400", description = "No active session on this table",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/tables/{tableId}/bill-preview")
    public ResponseEntity<BillPreviewDto> getBillPreview(Authentication authentication,
                                                          @PathVariable Long tableId) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Long restaurantId = principal.restaurantId();

        log.info("MANAGER_BILL_PREVIEW | restaurantId={} | tableId={}", restaurantId, tableId);

        TableSession session = sessionRepository.findOpenSessionByTableId(tableId)
                .orElseThrow(() -> new BusinessRuleException("No active session for table: " + tableId));

        if (!session.getRestaurant().getId().equals(restaurantId)) {
            throw new ResourceNotFoundException("Table not found: " + tableId);
        }

        BillPreviewDto preview = billCalculationService.computeBillPreview(session.getId());

        log.info("MANAGER_BILL_PREVIEW_SUCCESS | restaurantId={} | tableId={} | grandTotal={}",
                restaurantId, tableId, preview.grandTotal());

        return ResponseEntity.ok(preview);
    }

    @Operation(
            summary = "Close table session with payment recording",
            description = """
                    Atomically closes the session, finalizes the bill, generates the invoice,
                    and records the payment (amount received / change returned).
                    The amountReceived must be >= the bill grand total.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session closed and payment recorded",
                    content = @Content(schema = @Schema(implementation = CloseWithPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Business rule violation (underpayment, no active session)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Table not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/tables/{tableId}/session/close-with-payment")
    public ResponseEntity<CloseWithPaymentResponse> closeSessionWithPayment(
            Authentication authentication,
            @PathVariable Long tableId,
            @Valid @RequestBody CloseWithPaymentRequest request) {

        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();

        log.info("MANAGER_CLOSE_WITH_PAYMENT_REQUEST | restaurantId={} | tableId={} | amountReceived={} | tipAmount={}",
                principal.restaurantId(), tableId, request.amountReceived(), request.tipAmount());

        CloseWithPaymentResponse response = sessionService.closeSessionWithPayment(
                principal.restaurantId(),
                tableId,
                request.amountReceived(),
                request.tipAmount(),
                principal.userId()
        );

        log.info("MANAGER_CLOSE_WITH_PAYMENT_SUCCESS | restaurantId={} | tableId={} | invoiceNumber={} | tip={} | returned={}",
                principal.restaurantId(), tableId, response.invoiceNumber(), response.tipAmount(), response.amountReturned());

        return ResponseEntity.ok(response);
    }
}
