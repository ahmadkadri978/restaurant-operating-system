CREATE TABLE IF NOT EXISTS staff_push_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  restaurant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  token VARCHAR(512) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  UNIQUE KEY uq_user_token (user_id, token),
  INDEX idx_restaurant (restaurant_id)
);
