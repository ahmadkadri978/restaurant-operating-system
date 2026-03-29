package com.restaurantqr.finance;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "session_invoice_items")
public class SessionInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_invoice_id")
    private SessionInvoice sessionInvoice;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    public SessionInvoiceItem() {
    }

    public SessionInvoiceItem(SessionInvoice sessionInvoice, String itemName,
                              int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.sessionInvoice = sessionInvoice;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    // ==================== Getters & Setters ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public SessionInvoice getSessionInvoice() { return sessionInvoice; }
    public void setSessionInvoice(SessionInvoice sessionInvoice) { this.sessionInvoice = sessionInvoice; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}

