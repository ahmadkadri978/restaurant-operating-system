package com.restaurantqr.sse;

import com.restaurantqr.auth.dto.AuthPrincipal;
import com.restaurantqr.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * SSE endpoint for real-time event streaming.
 * Clients subscribe to receive order and service call updates.
 */
@Tag(name = "SSE - Real-time Events", description = "Server-Sent Events for real-time updates")
@RestController
@RequestMapping("/api/v1/sse")
@PreAuthorize("hasAnyRole('MANAGER', 'STAFF', 'KITCHEN')")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    /**
     * SSE connection timeout: 30 minutes.
     * After this time, the connection will be closed and client should reconnect.
     */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    private final SseEmitterRegistry registry;

    public SseController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @Operation(
            summary = "Subscribe to real-time events",
            description = """
                Opens a Server-Sent Events (SSE) connection for real-time updates.
                
                **Event Types:**
                - `ORDER_CREATED` - New order submitted
                - `ORDER_SENT_TO_KITCHEN` - Order sent to kitchen
                - `ORDER_DONE` - Order marked as done
                - `ORDER_CANCELLED` - Order cancelled
                - `SERVICE_CALL` - New service call
                - `HEARTBEAT` - Keep-alive signal (every 30s)
                - `CONNECTED` - Initial connection confirmation
                
                **Usage:**
                ```javascript
                const eventSource = new EventSource('/api/v1/sse/events?token=YOUR_JWT');
                eventSource.onmessage = (e) => {
                    const event = JSON.parse(e.data);
                    console.log(event.type, event.payload);
                };
                ```
                
                **Notes:**
                - Connection times out after 30 minutes (auto-reconnect recommended)
                - Events are filtered by restaurant ID from JWT
                - Browser EventSource will auto-reconnect on disconnection
                """,
            security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
    )
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication) {
        AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
        Long restaurantId = principal.restaurantId();

        log.info("SSE subscription request. restaurantId={}, userId={}, role={}", 
                restaurantId, principal.userId(), principal.role());

        // Create emitter with timeout
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // Register completion handlers
        emitter.onCompletion(() -> {
            log.info("SSE connection completed. restaurantId={}", restaurantId);
            registry.remove(restaurantId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timed out. restaurantId={}", restaurantId);
            registry.remove(restaurantId, emitter);
            emitter.complete();
        });

        emitter.onError(e -> {
            log.warn("SSE connection error. restaurantId={}", restaurantId, e);
            registry.remove(restaurantId, emitter);
        });

        // Register emitter in registry (may fail if limit reached)
        boolean registered = registry.register(restaurantId, emitter);
        if (!registered) {
            log.warn("SSE connection rejected - limit reached. restaurantId={}", restaurantId);
            try {
                emitter.send(SseEmitter.event()
                        .name("ERROR")
                        .data("{\"error\":\"Connection limit reached. Please try again later.\"}"));
                emitter.complete();
            } catch (IOException e) {
                // Ignore - just complete
            }
            return emitter;
        }

        // Send initial connection confirmation
        try {
            SseEvent<String> connectedEvent = SseEvent.connected(restaurantId);
            emitter.send(SseEmitter.event()
                    .name(SseEventType.CONNECTED.name())
                    .data(connectedEvent));
            log.info("SSE connection established. restaurantId={}, totalConnections={}", 
                    restaurantId, registry.getConnectionCount(restaurantId));
        } catch (IOException e) {
            log.error("Failed to send initial SSE event. restaurantId={}", restaurantId, e);
            registry.remove(restaurantId, emitter);
        }

        return emitter;
    }
}

