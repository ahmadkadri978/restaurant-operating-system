package com.restaurantqr.table;

import com.restaurantqr.restaurant.Restaurant;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "table_sessions")
public class TableSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @Column(name = "session_code", nullable = false, length = 4)
    private String sessionCode;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TableSession() {
    }

    public TableSession(Restaurant restaurant, RestaurantTable table, String sessionCode, Instant openedAt) {
        this.restaurant = restaurant;
        this.table = table;
        this.sessionCode = sessionCode;
        this.openedAt = openedAt;
        this.createdAt = Instant.now();
    }

    /**
     * Closes this session and calculates duration.
     */
    public void close(Instant closedAt) {
        this.closedAt = closedAt;
        if (this.openedAt != null && closedAt != null) {
            this.durationMinutes = (int) Duration.between(this.openedAt, closedAt).toMinutes();
        }
    }

    /**
     * Returns true if this session is still open (closedAt is null).
     */
    public boolean isOpen() {
        return this.closedAt == null;
    }

    // ==================== Getters & Setters ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public void setRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

