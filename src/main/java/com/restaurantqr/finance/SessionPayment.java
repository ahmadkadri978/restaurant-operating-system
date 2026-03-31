package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.TableSession;
import com.restaurantqr.user.User;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "session_payments")
public class SessionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "table_session_id", unique = true)
    private TableSession tableSession;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_bill_id")
    private SessionBill sessionBill;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "amount_received", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountReceived = BigDecimal.ZERO;

    @Column(name = "amount_returned", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountReturned = BigDecimal.ZERO;

    @Column(name = "tip_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod = "CASH";

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_register_session_id")
    private CashRegisterSession cashRegisterSession;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SessionPayment() {
    }

    public SessionPayment(Restaurant restaurant, TableSession tableSession, SessionBill sessionBill,
                          BigDecimal grandTotal, BigDecimal amountReceived, BigDecimal amountReturned,
                          BigDecimal tipAmount, String paymentMethod, Instant paidAt, User recordedBy) {
        this.restaurant = restaurant;
        this.tableSession = tableSession;
        this.sessionBill = sessionBill;
        this.grandTotal = grandTotal;
        this.amountReceived = amountReceived;
        this.amountReturned = amountReturned;
        this.tipAmount = tipAmount;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
        this.recordedBy = recordedBy;
        this.createdAt = Instant.now();
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Restaurant getRestaurant() { return restaurant; }
    public void setRestaurant(Restaurant restaurant) { this.restaurant = restaurant; }

    public TableSession getTableSession() { return tableSession; }
    public void setTableSession(TableSession tableSession) { this.tableSession = tableSession; }

    public SessionBill getSessionBill() { return sessionBill; }
    public void setSessionBill(SessionBill sessionBill) { this.sessionBill = sessionBill; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public BigDecimal getAmountReceived() { return amountReceived; }
    public void setAmountReceived(BigDecimal amountReceived) { this.amountReceived = amountReceived; }

    public BigDecimal getAmountReturned() { return amountReturned; }
    public void setAmountReturned(BigDecimal amountReturned) { this.amountReturned = amountReturned; }

    public BigDecimal getTipAmount() { return tipAmount; }
    public void setTipAmount(BigDecimal tipAmount) { this.tipAmount = tipAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }

    public CashRegisterSession getCashRegisterSession() { return cashRegisterSession; }
    public void setCashRegisterSession(CashRegisterSession cashRegisterSession) { this.cashRegisterSession = cashRegisterSession; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}




