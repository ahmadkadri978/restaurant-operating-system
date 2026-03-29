package com.restaurantqr.table;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    public StaffTableController(StaffTableSessionService sessionService) {
        this.sessionService = sessionService;
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

    @Operation(
            summary = "Close table session (remove code)",
            description = """
                    Closes the session for a given table by clearing sessionCode and sessionOpenedAt.
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
}

