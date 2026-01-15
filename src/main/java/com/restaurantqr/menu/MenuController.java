package com.restaurantqr.menu;

import com.restaurantqr.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public - Menu", description = "Customer endpoints (no JWT required)")
@RestController
@RequestMapping("/api/v1/public")
public class MenuController {

    private final MenuService menuService;

    private static final Logger log = LoggerFactory.getLogger(MenuController.class);
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @Operation(
            summary = "Get menu for a table QR token",
            description = """
                    Customer scans the table QR code and uses `qrToken` to fetch the menu.
                    
                    Returns active categories and available items for the table's restaurant.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Menu loaded successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MenuResponse.class))),
            @ApiResponse(responseCode = "404", description = "Invalid/inactive QR token",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "400", description = "Restaurant inactive",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @GetMapping("/tables/{qrToken}/menu")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable String qrToken) {
        log.debug("GET /public/tables/{}/menu", qrToken);
        return ResponseEntity.ok(menuService.getMenuByQrToken(qrToken));
    }
}

