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

    public SseHeartbeatScheduler(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Send heartbeat to all connected clients every 30 seconds.
     * This keeps the connection alive and helps detect stale connections.
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void sendHeartbeats() {
        int totalConnections = registry.getTotalConnectionCount();
        if (totalConnections > 0) {
            log.debug("Sending heartbeats to {} SSE connections", totalConnections);
            registry.sendHeartbeatToAll();
        }
    }
}

