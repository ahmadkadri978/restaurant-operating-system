package com.restaurantqr.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for managing public SSE connections for menu updates.
 * Separate from staff SSE to keep concerns isolated.
 * Thread-safe implementation using ConcurrentHashMap and CopyOnWriteArrayList.
 */
@Component
public class PublicMenuSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(PublicMenuSseRegistry.class);

    /**
     * Map of restaurantId -> list of active SSE emitters for customers.
     */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public PublicMenuSseRegistry() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Register a new SSE emitter for a restaurant's public menu stream.
     *
     * @param restaurantId the restaurant ID
     * @param emitter      the SSE emitter to register
     */
    public void register(Long restaurantId, SseEmitter emitter) {
        emitters.computeIfAbsent(restaurantId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("Public menu SSE emitter registered. restaurantId={}, totalConnections={}", 
                restaurantId, getConnectionCount(restaurantId));
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
            log.debug("Public menu SSE emitter removed. restaurantId={}, remainingConnections={}", 
                    restaurantId, list.size());
            
            // Clean up empty lists
            if (list.isEmpty()) {
                emitters.remove(restaurantId);
            }
        }
    }

    /**
     * Broadcast a menu event to all connected customers for a specific restaurant.
     *
     * @param restaurantId the restaurant ID to broadcast to
     * @param event        the event to broadcast
     */
    public void broadcast(Long restaurantId, SseEvent<?> event) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(restaurantId);
        if (list == null || list.isEmpty()) {
            log.debug("No public SSE connections for restaurantId={}, menu event not broadcast", restaurantId);
            return;
        }

        String eventData;
        try {
            eventData = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize public SSE event. restaurantId={}, type={}", 
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
                log.debug("Failed to send public SSE event, removing emitter. restaurantId={}", restaurantId);
                remove(restaurantId, emitter);
                failCount++;
            } catch (Exception e) {
                log.warn("Unexpected error sending public SSE event. restaurantId={}", restaurantId, e);
                remove(restaurantId, emitter);
                failCount++;
            }
        }

        log.debug("Public SSE broadcast complete. restaurantId={}, type={}, success={}, failed={}", 
                restaurantId, event.type(), successCount, failCount);
    }

    /**
     * Send a heartbeat to all connected customers for a specific restaurant.
     *
     * @param restaurantId the restaurant ID
     */
    public void sendHeartbeat(Long restaurantId) {
        broadcast(restaurantId, SseEvent.heartbeat(restaurantId));
    }

    /**
     * Send heartbeats to all connected customers across all restaurants.
     * Called by scheduler every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeatToAll() {
        if (emitters.isEmpty()) {
            return;
        }
        log.debug("Sending public menu SSE heartbeats to {} restaurants", emitters.size());
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
     * @return total number of active public SSE connections
     */
    public int getTotalConnectionCount() {
        return emitters.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Get all restaurant IDs with active public connections.
     *
     * @return list of restaurant IDs
     */
    public List<Long> getActiveRestaurantIds() {
        return List.copyOf(emitters.keySet());
    }
}

