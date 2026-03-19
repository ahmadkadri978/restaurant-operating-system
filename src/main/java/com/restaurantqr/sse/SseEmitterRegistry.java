package com.restaurantqr.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for managing SSE connections per restaurant.
 * Thread-safe implementation using ConcurrentHashMap and CopyOnWriteArrayList.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    /**
     * Maximum connections per restaurant to prevent memory exhaustion.
     */
    @Value("${app.sse.max-connections-per-restaurant:500}")
    private int maxConnectionsPerRestaurant = 500;

    /**
     * Map of restaurantId -> list of active SSE emitters.
     */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public SseEmitterRegistry() {
        // Create ObjectMapper with Java 8 time support
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Register a new SSE emitter for a restaurant.
     * Returns false if connection limit is reached.
     *
     * @param restaurantId the restaurant ID
     * @param emitter      the SSE emitter to register
     * @return true if registered, false if limit reached
     */
    public boolean register(Long restaurantId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.computeIfAbsent(restaurantId, k -> new CopyOnWriteArrayList<>());
        
        // Check connection limit
        if (list.size() >= maxConnectionsPerRestaurant) {
            log.warn("SSE connection limit reached. restaurantId={}, limit={}", 
                    restaurantId, maxConnectionsPerRestaurant);
            return false;
        }
        
        list.add(emitter);
        log.info("SSE emitter registered. restaurantId={}, totalConnections={}", 
                restaurantId, getConnectionCount(restaurantId));
        return true;
    }

    /**
     * Remove an SSE emitter from the registry.
     *
     * @param restaurantId the restaurant ID
     * @param emitter      the SSE emitter to remove
     */
    public void remove(Long restaurantId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(restaurantId);
        if (list != null) {
            list.remove(emitter);
            log.info("SSE emitter removed. restaurantId={}, remainingConnections={}", 
                    restaurantId, list.size());
            
            // Clean up empty lists
            if (list.isEmpty()) {
                emitters.remove(restaurantId);
            }
        }
    }

    /**
     * Broadcast an event to all connected clients for a specific restaurant.
     *
     * @param restaurantId the restaurant ID to broadcast to
     * @param event        the event to broadcast
     */
    public void broadcast(Long restaurantId, SseEvent<?> event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(restaurantId);
        if (list == null || list.isEmpty()) {
            log.debug("No SSE connections for restaurantId={}, event not broadcast", restaurantId);
            return;
        }

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize SSE event. restaurantId={}, type={}", 
                    restaurantId, event.type(), e);
            return;
        }

        int successCount = 0;
        int failCount = 0;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.type().name())
                        .data(eventData));
                successCount++;
            } catch (IOException e) {
                log.debug("Failed to send SSE event, removing emitter. restaurantId={}", restaurantId);
                remove(restaurantId, emitter);
                failCount++;
            } catch (Exception e) {
                log.warn("Unexpected error sending SSE event. restaurantId={}", restaurantId, e);
                remove(restaurantId, emitter);
                failCount++;
            }
        }

        log.debug("SSE broadcast complete. restaurantId={}, type={}, success={}, failed={}", 
                restaurantId, event.type(), successCount, failCount);
    }

    /**
     * Send a heartbeat to all connected clients for a specific restaurant.
     *
     * @param restaurantId the restaurant ID
     */
    public void sendHeartbeat(Long restaurantId) {
        broadcast(restaurantId, SseEvent.heartbeat(restaurantId));
    }

    /**
     * Send heartbeats to all connected clients across all restaurants.
     */
    public void sendHeartbeatToAll() {
        for (Long restaurantId : emitters.keySet()) {
            sendHeartbeat(restaurantId);
        }
    }

    /**
     * Get the number of active connections for a restaurant.
     *
     * @param restaurantId the restaurant ID
     * @return the number of active connections
     */
    public int getConnectionCount(Long restaurantId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(restaurantId);
        return list != null ? list.size() : 0;
    }

    /**
     * Get total connection count across all restaurants.
     *
     * @return total number of active SSE connections
     */
    public int getTotalConnectionCount() {
        return emitters.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Get all restaurant IDs with active connections.
     *
     * @return list of restaurant IDs
     */
    public List<Long> getActiveRestaurantIds() {
        return List.copyOf(emitters.keySet());
    }
}

