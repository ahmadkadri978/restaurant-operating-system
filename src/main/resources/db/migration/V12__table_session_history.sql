-- =========================================
-- Table session history — persists every open/close lifecycle
-- =========================================

CREATE TABLE table_sessions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    restaurant_id     BIGINT       NOT NULL,
    table_id          BIGINT       NOT NULL,
    session_code      VARCHAR(4)   NOT NULL,
    opened_at         DATETIME(6)  NOT NULL,
    closed_at         DATETIME(6)  NULL,
    duration_minutes  INT          NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_table_sessions_restaurant_opened (restaurant_id, opened_at),
    INDEX idx_table_sessions_restaurant_closed (restaurant_id, closed_at),
    INDEX idx_table_sessions_table (table_id),
    CONSTRAINT fk_table_sessions_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_table_sessions_table
        FOREIGN KEY (table_id) REFERENCES restaurant_tables(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

