package com.restaurantqr.finance;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.exception.BusinessRuleException;
import com.restaurantqr.finance.dto.InvoiceListDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Manager/Owner - Invoices", description = "Session invoice management (list, search, PDF download)")
@RestController
@RequestMapping("/api/v1/manager/invoices")
@PreAuthorize("hasAnyRole('MANAGER', 'OWNER')")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @Operation(
            summary = "List or search invoices",
            description = """
                    Returns invoices filtered by date or searched by invoice number.
                    
                    **Usage modes:**
                    1. **By date**: provide `date` parameter → returns all invoices for that day, newest first
                    2. **By invoice number**: provide `invoiceNumber` parameter → returns matching invoice
                    
                    At least one of `date` or `invoiceNumber` must be provided.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Invoices retrieved"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid parameters",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<InvoiceListDto>> listInvoices(
            Authentication authentication,

            @Parameter(description = "Filter by date (ISO format: yyyy-MM-dd)", example = "2026-03-29")
            @RequestParam(required = false) LocalDate date,

            @Parameter(description = "Search by invoice number", example = "INV-20260329-003")
            @RequestParam(required = false) String invoiceNumber,

            @Parameter(description = "Timezone for date calculations", example = "Asia/Damascus")
            @RequestParam(defaultValue = "Asia/Damascus") String zone
    ) {
        Long restaurantId = extractRestaurantId(authentication);

        // Search by invoice number takes priority
        if (invoiceNumber != null && !invoiceNumber.isBlank()) {
            log.info("SEARCH_INVOICES | restaurantId={} | invoiceNumber={}", restaurantId, invoiceNumber);

            List<InvoiceListDto> result = invoiceService.searchByNumber(restaurantId, invoiceNumber);

            log.info("SEARCH_INVOICES_SUCCESS | restaurantId={} | found={}", restaurantId, result.size());
            return ResponseEntity.ok(result);
        }

        // List by date
        if (date == null) {
            throw new BusinessRuleException("Either 'date' or 'invoiceNumber' parameter is required");
        }

        log.info("LIST_INVOICES | restaurantId={} | date={} | zone={}", restaurantId, date, zone);

        List<InvoiceListDto> result = invoiceService.listByDate(restaurantId, date, zone);

        log.info("LIST_INVOICES_SUCCESS | restaurantId={} | date={} | count={}", restaurantId, date, result.size());
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Download invoice as PDF",
            description = "Generates and returns the invoice as a downloadable PDF file.",
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "PDF generated",
                    content = @Content(mediaType = "application/pdf")),
            @ApiResponse(responseCode = "404", description = "Invoice not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            Authentication authentication,
            @PathVariable Long invoiceId
    ) {
        Long restaurantId = extractRestaurantId(authentication);

        log.info("DOWNLOAD_INVOICE_PDF | restaurantId={} | invoiceId={}", restaurantId, invoiceId);

        byte[] pdfBytes = invoiceService.generatePdf(restaurantId, invoiceId);

        log.info("DOWNLOAD_INVOICE_PDF_SUCCESS | restaurantId={} | invoiceId={} | bytes={}",
                restaurantId, invoiceId, pdfBytes.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + invoiceId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
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

