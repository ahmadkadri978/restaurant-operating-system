package com.restaurantqr.sse;

import com.restaurantqr.exception.ResourceNotFoundException;
import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.PublicSessionService;
import com.restaurantqr.table.RestaurantTable;
import com.restaurantqr.table.RestaurantTableRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Public SSE endpoint for real-time menu updates.
 * No JWT required - uses qrToken + sessionCode for restaurant scoping.
 */
@Tag(name = "Public - SSE", description = "Server-Sent Events for customers (no JWT required)")
@RestController
@RequestMapping("/api/v1/public/sse")
public class PublicSseController {

    private static final Logger log = LoggerFactory.getLogger(PublicSseController.class);

    /**
     * SSE connection timeout: 30 minutes.
     */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final PublicMenuSseRegistry registry;
    private final PublicSessionService publicSessionService;
    private final RestaurantTableRepository tableRepository;

    public PublicSseController(
            PublicMenuSseRegistry registry,
            PublicSessionService publicSessionService,
            RestaurantTableRepository tableRepository
    ) {
        this.registry = registry;
        this.publicSessionService = publicSessionService;
        this.tableRepository = tableRepository;
    }

    @Operation(
            summary = "Subscribe to real-time menu updates",
            description = """
                Opens a Server-Sent Events (SSE) connection for real-time menu updates.
                
                **Event Types:**
                - `MENU_ITEM_UPDATED` - Menu item was created or updated
                - `MENU_ITEM_DELETED` - Menu item was deleted
                - `HEARTBEAT` - Keep-alive signal (every 30s)
                - `CONNECTED` - Initial connection confirmation
                
                **Usage:**
                ```javascript
                const token = getToken();
                const code = getCode();
                const eventSource = new EventSource(
                    `/api/v1/public/sse/menu?token=${token}&code=${code}`
                );
                eventSource.addEventListener('MENU_ITEM_UPDATED', (e) => {
                    const event = JSON.parse(e.data);
                    updateMenuItemInDOM(event.payload);
                });
                ```
                
                **Notes:**
                - Connection times out after 30 minutes (auto-reconnect recommended)
                - Events are filtered by restaurant (derived from qrToken)
                - Browser EventSource will auto-reconnect on disconnection
                """
    )
    @GetMapping(value = "/menu", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToMenu(
            @Parameter(description = "QR token from the table")
            @RequestParam("token") String qrToken,
            @Parameter(description = "Session code (4-digit)")
            @RequestParam("code") String sessionCode
    ) {
        log.info("Public menu SSE subscription request. qrToken={}", qrToken);

        // Validate session
        publicSessionService.verifySessionOrThrow(qrToken, sessionCode);

        // Get restaurant from qrToken
        RestaurantTable table = tableRepository.findActiveByQrTokenFetchRestaurant(qrToken)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or inactive QR token"));

        Restaurant restaurant = table.getRestaurant();
        if (restaurant == null || !restaurant.isActive()) {
            throw new ResourceNotFoundException("Restaurant not found or inactive");
        }

        Long restaurantId = restaurant.getId();

        // Create emitter with timeout
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Register completion handlers
        emitter.onCompletion(() -> {
            log.debug("Public menu SSE connection completed. restaurantId={}", restaurantId);
            registry.remove(restaurantId, emitter);
        });

        emitter.onTimeout(() -> {
            log.debug("Public menu SSE connection timed out. restaurantId={}", restaurantId);
            registry.remove(restaurantId, emitter);
            emitter.complete();
        });

        emitter.onError(e -> {
            log.debug("Public menu SSE connection error. restaurantId={}", restaurantId);
            registry.remove(restaurantId, emitter);
        });

        // Register emitter in registry
        registry.register(restaurantId, emitter);

        // Send initial connection confirmation
        try {
            SseEvent<String> connectedEvent = SseEvent.connected(restaurantId);
            emitter.send(SseEmitter.event()
                    .name(SseEventType.CONNECTED.name())
                    .data(connectedEvent));
            log.info("Public menu SSE connection established. restaurantId={}, tableNumber={}, totalConnections={}", 
                    restaurantId, table.getTableNumber(), registry.getConnectionCount(restaurantId));
        } catch (IOException e) {
            log.error("Failed to send initial public SSE event. restaurantId={}", restaurantId, e);
            registry.remove(restaurantId, emitter);
        }

        return emitter;
    }
}

