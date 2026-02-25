ALTER TABLE restaurant_tables
  ADD COLUMN session_code VARCHAR(4) NULL,
  ADD COLUMN session_opened_at DATETIME(6) NULL;

CREATE INDEX idx_tables_rest_session_code
  ON restaurant_tables (restaurant_id, session_code);
