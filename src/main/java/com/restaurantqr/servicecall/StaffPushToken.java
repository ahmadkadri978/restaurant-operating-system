package com.restaurantqr.servicecall;

import jakarta.persistence.*;


import java.time.Instant;


@Entity
@Table(name = "staff_push_tokens",
        uniqueConstraints = @UniqueConstraint(name="uq_user_token", columnNames={"user_id","token"}))
public class StaffPushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="restaurant_id", nullable=false)
    private Long restaurantId;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(nullable=false, length=512)
    private String token;

    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(Long restaurantId) {
        this.restaurantId = restaurantId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
