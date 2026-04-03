package com.restaurantqr.finance;

import jakarta.persistence.*;

@Entity
@Table(
        name = "restaurant_invoice_counters",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_invoice_counter_restaurant_date",
                columnNames = {"restaurant_id", "date_str"}
        )
)
public class InvoiceCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "date_str", nullable = false, length = 8)
    private String dateStr;

    @Column(name = "last_seq", nullable = false)
    private Integer lastSeq = 0;

    public InvoiceCounter() {
    }

    public InvoiceCounter(Long restaurantId, String dateStr, Integer lastSeq) {
        this.restaurantId = restaurantId;
        this.dateStr = dateStr;
        this.lastSeq = lastSeq;
    }

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

    public String getDateStr() {
        return dateStr;
    }

    public void setDateStr(String dateStr) {
        this.dateStr = dateStr;
    }

    public Integer getLastSeq() {
        return lastSeq;
    }

    public void setLastSeq(Integer lastSeq) {
        this.lastSeq = lastSeq;
    }
}

