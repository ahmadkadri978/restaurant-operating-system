package com.restaurantqr.table;

import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public")
public class PublicSessionController {
    private static final Logger log = LoggerFactory.getLogger(PublicSessionController.class);

    private final PublicSessionService publicSessionService;

    public PublicSessionController(PublicSessionService publicSessionService) {
        this.publicSessionService = publicSessionService;
    }
    @Operation(
            summary = "Verify table session code (4 digits)",
            description = """
                Customer verifies the 4-digit session code provided by staff.
                If valid, customer can access menu and place orders.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verified"),
            @ApiResponse(responseCode = "400", description = "Invalid session code",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Table/restaurant not found or inactive",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })


    @PostMapping("/tables/{qrToken}/session/verify")
    public VerifySessionResponse verify(@PathVariable String qrToken, @Valid @RequestBody VerifySessionRequest req) {
        log.info("PUBLIC_VERIFY_SESSION_REQUEST | qrToken={}", qrToken);

        publicSessionService.verifySessionOrThrow(qrToken, req.code());

        log.info("PUBLIC_VERIFY_SESSION_SUCCESS | qrToken={}", qrToken);

        return new VerifySessionResponse(true);
    }
}

