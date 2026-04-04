package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.TableSession;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_bills")
public class SessionBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id", unique = true)
    private TableSession tableSession;

    @Column(name = "items_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsSubtotal = BigDecimal.ZERO;

    @Column(name = "charges_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargesTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "SYP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "sessionBill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("applyOrder ASC")
    private List<SessionBillCharge> charges = new ArrayList<>();

    public SessionBill() {
    }

    public SessionBill(Restaurant restaurant, TableSession tableSession) {
        this.restaurant = restaurant;
        this.tableSession = tableSession;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isFinalized() {
        return this.status == BillStatus.FINALIZED;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public TableSession getTableSession() { return tableSession; }
    public void setTableSession(TableSession tableSession) { this.tableSession = tableSession; }

    public BigDecimal getItemsSubtotal() { return itemsSubtotal; }
    public void setItemsSubtotal(BigDecimal itemsSubtotal) { this.itemsSubtotal = itemsSubtotal; }

    public BigDecimal getChargesTotal() { return chargesTotal; }
    public void setChargesTotal(BigDecimal chargesTotal) { this.chargesTotal = chargesTotal; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BillStatus getStatus() { return status; }
    public void setStatus(BillStatus status) { this.status = status; }

    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<SessionBillCharge> getCharges() { return charges; }
    public void setCharges(List<SessionBillCharge> charges) { this.charges = charges; }
}

