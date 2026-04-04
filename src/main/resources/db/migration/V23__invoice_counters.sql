-- =========================================
-- V23: Invoice Counters
-- Concurrency-safe daily invoice sequence per restaurant
-- =========================================

CREATE TABLE restaurant_invoice_counters (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    restaurant_id  BIGINT       NOT NULL,
    date_str       VARCHAR(8)   NOT NULL,
    last_seq       INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_counter_restaurant_date (restaurant_id, date_str),
    CONSTRAINT fk_invoice_counter_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

