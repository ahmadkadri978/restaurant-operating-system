ALTER TABLE customer_orders
  ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'NEW',
  ADD COLUMN sent_to_kitchen_at DATETIME(6) NULL;

CREATE INDEX idx_customer_orders_restaurant_status_created
  ON customer_orders (restaurant_id, status, created_at DESC);
