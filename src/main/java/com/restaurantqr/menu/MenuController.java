package com.restaurantqr.menu;

import com.restaurantqr.exception.ApiErrorResponse;
import com.restaurantqr.table.PublicSessionService;
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
    private final PublicSessionService publicSessionService;

    private static final Logger log = LoggerFactory.getLogger(MenuController.class);
    public MenuController(MenuService menuService, PublicSessionService publicSessionService) {
        this.menuService = menuService;
        this.publicSessionService = publicSessionService;
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
    public ResponseEntity<MenuResponse> getMenu(@PathVariable String qrToken, @RequestParam String code) {
        log.info("PUBLIC_GET_MENU_REQUEST | qrToken={} | codeProvided={}",
                qrToken, code != null);
        publicSessionService.verifySessionOrThrow(qrToken, code);
        MenuResponse response = menuService.getMenuByQrToken(qrToken);
        log.info("PUBLIC_GET_MENU_SUCCESS | qrToken={} | categoriesCount={} | itemsCount={}",
                qrToken,
                response.categories().size(),
                response.items().size());
        return response != null ? ResponseEntity.ok(response) : ResponseEntity.notFound().build();
    }
}

