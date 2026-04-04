package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cash_movements")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_session_id")
    private CashRegisterSession cashRegisterSession;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CashMovementType type;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public CashMovement() {
    }

    public CashMovement(CashRegisterSession cashRegisterSession, Restaurant restaurant,
                        CashMovementType type, BigDecimal amount, String reason, User recordedBy) {
        this.cashRegisterSession = cashRegisterSession;
        this.restaurant = restaurant;
        this.type = type;
        this.amount = amount;
        this.reason = reason;
        this.recordedBy = recordedBy;
        this.createdAt = Instant.now();
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public CashRegisterSession getCashRegisterSession() { return cashRegisterSession; }
    public void setCashRegisterSession(CashRegisterSession cashRegisterSession) { this.cashRegisterSession = cashRegisterSession; }

    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public CashMovementType getType() { return type; }
    public void setType(CashMovementType type) { this.type = type; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

