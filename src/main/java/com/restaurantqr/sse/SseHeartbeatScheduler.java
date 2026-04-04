package com.restaurantqr.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for sending periodic heartbeats to all SSE connections.
 * Heartbeats keep connections alive and allow clients to detect connection issues.
 */
@Component
@EnableScheduling
public class SseHeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(SseHeartbeatScheduler.class);

    private final SseEmitterRegistry registry;
    private final PublicMenuSseRegistry publicMenuRegistry;

    public SseHeartbeatScheduler(SseEmitterRegistry registry, PublicMenuSseRegistry publicMenuRegistry) {
        this.registry = registry;
        this.publicMenuRegistry = publicMenuRegistry;
    }

    /**
     * Send heartbeat to all connected staff clients every 15 seconds.
     * This keeps the connection alive and helps detect stale connections quickly.
     * Reduced from 30s to 15s to detect dead connections faster.
     */
    @Scheduled(fixedRate = 15000) // 15 seconds
    public void sendHeartbeats() {
        int totalConnections = registry.getTotalConnectionCount();
        if (totalConnections > 0) {
            log.debug("Sending heartbeats to {} staff SSE connections", totalConnections);
            registry.sendHeartbeatToAll();
        }
    }

    /**
     * Send heartbeat to all connected public menu SSE clients every 20 seconds.
     */
    @Scheduled(fixedRate = 20000) // 20 seconds
    public void sendPublicHeartbeats() {
        int totalConnections = publicMenuRegistry.getTotalConnectionCount();
        if (totalConnections > 0) {
            log.debug("Sending heartbeats to {} public menu SSE connections", totalConnections);
            publicMenuRegistry.sendHeartbeatToAll();
        }
    }
}

