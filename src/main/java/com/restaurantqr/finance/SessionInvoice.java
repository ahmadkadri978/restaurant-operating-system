package com.restaurantqr.finance;

import com.restaurantqr.restaurant.Restaurant;
import com.restaurantqr.table.TableSession;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "session_invoices")
public class SessionInvoice {

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
    @JoinColumn(name = "session_bill_id", unique = true)
    private SessionBill sessionBill;

    @Column(name = "invoice_number", nullable = false, length = 30)
    private String invoiceNumber;

    @Column(name = "table_number", nullable = false)
    private int tableNumber;

    @Column(name = "session_code", nullable = false, length = 4)
    private String sessionCode;

    @Column(name = "session_opened_at", nullable = false)
    private Instant sessionOpenedAt;

    @Column(name = "session_closed_at", nullable = false)
    private Instant sessionClosedAt;

    @Column(name = "items_subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal itemsSubtotal = BigDecimal.ZERO;

    @Column(name = "charges_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal chargesTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String currency = "SYP";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "sessionInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SessionInvoiceItem> items = new ArrayList<>();

    public SessionInvoice() {
    }

    public SessionInvoice(Restaurant restaurant, TableSession tableSession, SessionBill sessionBill,
                          String invoiceNumber, int tableNumber, String sessionCode,
                          Instant sessionOpenedAt, Instant sessionClosedAt,
                          BigDecimal itemsSubtotal, BigDecimal chargesTotal, BigDecimal grandTotal,
                          String currency) {
        this.restaurant = restaurant;
        this.tableSession = tableSession;
        this.sessionBill = sessionBill;
        this.invoiceNumber = invoiceNumber;
        this.tableNumber = tableNumber;
        this.sessionCode = sessionCode;
        this.sessionOpenedAt = sessionOpenedAt;
        this.sessionClosedAt = sessionClosedAt;
        this.itemsSubtotal = itemsSubtotal;
        this.chargesTotal = chargesTotal;
        this.grandTotal = grandTotal;
        this.currency = currency;
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

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }

    public String getSessionCode() { return sessionCode; }
    public void setSessionCode(String sessionCode) { this.sessionCode = sessionCode; }

    public Instant getSessionOpenedAt() { return sessionOpenedAt; }
    public void setSessionOpenedAt(Instant sessionOpenedAt) { this.sessionOpenedAt = sessionOpenedAt; }

    public Instant getSessionClosedAt() { return sessionClosedAt; }
    public void setSessionClosedAt(Instant sessionClosedAt) { this.sessionClosedAt = sessionClosedAt; }

    public BigDecimal getItemsSubtotal() { return itemsSubtotal; }
    public void setItemsSubtotal(BigDecimal itemsSubtotal) { this.itemsSubtotal = itemsSubtotal; }

    public BigDecimal getChargesTotal() { return chargesTotal; }
    public void setChargesTotal(BigDecimal chargesTotal) { this.chargesTotal = chargesTotal; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<SessionInvoiceItem> getItems() { return items; }
    public void setItems(List<SessionInvoiceItem> items) { this.items = items; }
}

