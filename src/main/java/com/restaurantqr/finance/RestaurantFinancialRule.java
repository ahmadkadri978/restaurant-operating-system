package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "restaurant_financial_rules")
public class RestaurantFinancialRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @Column(name = "name_ar", nullable = false)
    private String nameAr;

    @Column(name = "name_en")
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private RuleType ruleType;

    @Column(name = "\"value\"", nullable = false, precision = 10, scale = 4)
    private BigDecimal value;

    @Column(name = "apply_order", nullable = false)
    private Integer applyOrder = 0;

    @Column(name = "is_compound", nullable = false)
    private boolean isCompound = false;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RestaurantFinancialRule() {
    }

    public RestaurantFinancialRule(Restaurant restaurant, String nameAr, String nameEn,
                                   RuleType ruleType, BigDecimal value, Integer applyOrder,
                                   boolean isCompound) {
        this.restaurant = restaurant;
        this.nameAr = nameAr;
        this.nameEn = nameEn;
        this.ruleType = ruleType;
        this.value = value;
        this.applyOrder = applyOrder;
        this.isCompound = isCompound;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
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

    public String getNameAr() {
        return nameAr;
    }

    public void setNameAr(String nameAr) {
        this.nameAr = nameAr;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(RuleType ruleType) {
        this.ruleType = ruleType;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public Integer getApplyOrder() {
        return applyOrder;
    }

    public void setApplyOrder(Integer applyOrder) {
        this.applyOrder = applyOrder;
    }

    public boolean isCompound() {
        return isCompound;
    }

    public void setCompound(boolean compound) {
        isCompound = compound;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

