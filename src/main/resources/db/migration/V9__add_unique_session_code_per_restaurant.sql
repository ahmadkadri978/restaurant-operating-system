DROP INDEX idx_tables_rest_session_code
ON restaurant_tables;
ALTER TABLE restaurant_tables
ADD CONSTRAINT uq_restaurant_session_code
UNIQUE (restaurant_id, session_code);