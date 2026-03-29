-- =========================================
-- V19: Session Invoices
-- Formal invoice documents generated when sessions close
-- =========================================

CREATE TABLE session_invoices (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    restaurant_id      BIGINT         NOT NULL,
    table_session_id   BIGINT         NOT NULL,
    session_bill_id    BIGINT         NOT NULL,
    invoice_number     VARCHAR(30)    NOT NULL,
    table_number       INT            NOT NULL,
    session_code       VARCHAR(4)     NOT NULL,
    session_opened_at  DATETIME(6)    NOT NULL,
    session_closed_at  DATETIME(6)    NOT NULL,
    items_subtotal     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    charges_total      DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    grand_total        DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    currency           VARCHAR(10)    NOT NULL DEFAULT 'SYP',
    created_at         DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_invoice_session (table_session_id),
    UNIQUE KEY uk_invoice_number_restaurant (restaurant_id, invoice_number),
    INDEX idx_invoice_restaurant_created (restaurant_id, created_at),
    CONSTRAINT fk_invoice_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_invoice_session
        FOREIGN KEY (table_session_id) REFERENCES table_sessions(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_invoice_bill
        FOREIGN KEY (session_bill_id) REFERENCES session_bills(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_invoice_items (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    session_invoice_id  BIGINT         NOT NULL,
    item_name           VARCHAR(255)   NOT NULL,
    quantity            INT            NOT NULL,
    unit_price          DECIMAL(10,2)  NOT NULL,
    line_total          DECIMAL(10,2)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_invoice_items_invoice (session_invoice_id),
    CONSTRAINT fk_invoice_items_invoice
        FOREIGN KEY (session_invoice_id) REFERENCES session_invoices(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

