package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_register_sessions")
public class CashRegisterSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "opened_by")
    private User openedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "expected_closing", precision = 12, scale = 2)
    private BigDecimal expectedClosing;

    @Column(name = "actual_closing", precision = 12, scale = 2)
    private BigDecimal actualClosing;

    @Column(name = "discrepancy", precision = 12, scale = 2)
    private BigDecimal discrepancy;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CashRegisterStatus status = CashRegisterStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CashRegisterSession() {
    }

    public CashRegisterSession(Restaurant restaurant, User openedBy, BigDecimal openingBalance, Instant openedAt) {
        this.restaurant = restaurant;
        this.openedBy = openedBy;
        this.openingBalance = openingBalance;
        this.openedAt = openedAt;
        this.status = CashRegisterStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isOpen() {
        return status == CashRegisterStatus.OPEN;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public User getOpenedBy() { return openedBy; }
    public void setOpenedBy(User openedBy) { this.openedBy = openedBy; }

    public User getClosedBy() { return closedBy; }
    public void setClosedBy(User closedBy) { this.closedBy = closedBy; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

    public BigDecimal getExpectedClosing() { return expectedClosing; }
    public void setExpectedClosing(BigDecimal expectedClosing) { this.expectedClosing = expectedClosing; }

    public BigDecimal getActualClosing() { return actualClosing; }
    public void setActualClosing(BigDecimal actualClosing) { this.actualClosing = actualClosing; }

    public BigDecimal getDiscrepancy() { return discrepancy; }
    public void setDiscrepancy(BigDecimal discrepancy) { this.discrepancy = discrepancy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public CashRegisterStatus getStatus() { return status; }
    public void setStatus(CashRegisterStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

