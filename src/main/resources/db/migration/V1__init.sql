-- =========================================
-- Restaurant QR System - MySQL 8+ Schema- V1__init.sql
-- =========================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------
-- restaurants
-- -------------------------

CREATE TABLE restaurants (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  code         VARCHAR(255) NULL,
  name         VARCHAR(255) NOT NULL,
  is_active    TINYINT(1) NOT NULL DEFAULT 1,
  created_at   DATETIME(6) NOT NULL,
  updated_at   DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_restaurants_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- users
-- -------------------------

CREATE TABLE users (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  username      VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role          ENUM('MANAGER','STAFF') NOT NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  restaurant_id BIGINT NOT NULL,
  created_at    DATETIME(6) NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_restaurant (restaurant_id),
  CONSTRAINT fk_users_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- restaurant_tables
-- -------------------------

CREATE TABLE restaurant_tables (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT NOT NULL,
  table_number  INT NOT NULL,
  qr_token      VARCHAR(255) NOT NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  created_at    DATETIME(6) NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_restaurant_tables_qr_token (qr_token),
  KEY idx_tables_restaurant (restaurant_id),
  CONSTRAINT fk_tables_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- menu_categories
-- -------------------------

CREATE TABLE menu_categories (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT NOT NULL,
  name          VARCHAR(255) NOT NULL,
  display_order INT NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  created_at    DATETIME(6) NOT NULL,
  updated_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_menu_categories_restaurant (restaurant_id),
  KEY idx_menu_categories_restaurant_order (restaurant_id, display_order),
  CONSTRAINT fk_menu_categories_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- menu_items
-- -------------------------

CREATE TABLE menu_items (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  restaurant_id    BIGINT NOT NULL,
  menu_category_id BIGINT NOT NULL,
  name             VARCHAR(255) NOT NULL,
  description      TEXT NULL,
  price            DECIMAL(10,2) NOT NULL,
  is_available     TINYINT(1) NOT NULL DEFAULT 1,
  is_active        TINYINT(1) NOT NULL DEFAULT 1,
  display_order    INT NULL,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_menu_items_restaurant (restaurant_id),
  KEY idx_menu_items_category (menu_category_id),
  KEY idx_menu_items_restaurant_active_available (restaurant_id, is_active, is_available, display_order),
  CONSTRAINT fk_menu_items_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_menu_items_category
    FOREIGN KEY (menu_category_id) REFERENCES menu_categories(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- customer_orders
-- -------------------------

CREATE TABLE customer_orders (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT NOT NULL,
  table_id      BIGINT NOT NULL,
  total_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  note          TEXT NULL,
  created_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_orders_restaurant_created_at (restaurant_id, created_at DESC),
  KEY idx_orders_table_created_at (table_id, created_at DESC),
  CONSTRAINT fk_orders_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_orders_table
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- order_items
-- -------------------------

CREATE TABLE order_items (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  customer_order_id BIGINT NOT NULL,
  menu_item_id     BIGINT NOT NULL,
  quantity         INT NOT NULL,
  unit_price       DECIMAL(10,2) NOT NULL,
  total_price      DECIMAL(10,2) NOT NULL,
  note             TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_order_items_order (customer_order_id),
  KEY idx_order_items_menu_item (menu_item_id),
  CONSTRAINT fk_order_items_order
    FOREIGN KEY (customer_order_id) REFERENCES customer_orders(id)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT fk_order_items_menu_item
    FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -------------------------
-- service_calls
-- -------------------------

CREATE TABLE service_calls (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  restaurant_id BIGINT NOT NULL,
  table_id      BIGINT NOT NULL,
  call_type     ENUM('WAITER','BILL','NARA') NOT NULL,
  created_at    DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_service_calls_restaurant_created_at (restaurant_id, created_at DESC),
  KEY idx_service_calls_table_created_at (table_id, created_at DESC),
  CONSTRAINT fk_service_calls_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT fk_service_calls_table
    FOREIGN KEY (table_id) REFERENCES restaurant_tables(id)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
