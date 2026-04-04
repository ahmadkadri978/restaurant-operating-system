CREATE INDEX idx_customer_orders_restaurant_created
ON customer_orders (restaurant_id, created_at);

CREATE INDEX idx_service_calls_restaurant_created
ON service_calls (restaurant_id, created_at);