USE restaurantdb;

START TRANSACTION;

-- =====================================
-- (A) Create restaurant
-- =====================================
INSERT INTO restaurants (code, name, is_active, created_at, updated_at)
VALUES ('REST-1', 'rabwa Restaurant', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

SET @restaurant_id = LAST_INSERT_ID();

-- =====================================
-- (B) Create STAFF user for this restaurant
-- NOTE: password_hash should be generated inside your Spring Boot app (BCrypt)
-- =====================================
INSERT INTO users (username, password_hash, role, is_active, restaurant_id, created_at, updated_at)
VALUES (
  'staff_rest001',
  '$2a$12$A6Y31VCkJ6Bu4d4IyhaKFeGnbm6JMYgnR8SWO1yYJf7RRn2WI6FUG',
  'STAFF',
  1,
  @restaurant_id,
  UTC_TIMESTAMP(6),
  UTC_TIMESTAMP(6)
);

SET @staff_user_id = LAST_INSERT_ID();

-- =====================================
-- (C) Seed default menu categories
-- =====================================
INSERT INTO menu_categories (restaurant_id, name, display_order, is_active, created_at, updated_at)
VALUES
(@restaurant_id, 'Drinks',   1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 'Meals',    2, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 'Desserts', 3, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Fetch category ids into variables
SELECT id INTO @cat_drinks_id
FROM menu_categories
WHERE restaurant_id = @restaurant_id AND name = 'Drinks'
ORDER BY id DESC LIMIT 1;

SELECT id INTO @cat_meals_id
FROM menu_categories
WHERE restaurant_id = @restaurant_id AND name = 'Meals'
ORDER BY id DESC LIMIT 1;

SELECT id INTO @cat_desserts_id
FROM menu_categories
WHERE restaurant_id = @restaurant_id AND name = 'Desserts'
ORDER BY id DESC LIMIT 1;

-- =====================================
-- (D) Seed menu items for each category
-- =====================================

-- Drinks
INSERT INTO menu_items (
  restaurant_id, menu_category_id, name, description, price,
  is_available, is_active, display_order, created_at, updated_at, image_url
)
VALUES
(@restaurant_id, @cat_drinks_id, 'Water',        'Still water 500ml', 1.50, 1, 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),'/uploads/REST-1/water.jpg'),
(@restaurant_id, @cat_drinks_id, 'Coca Cola',    '330ml can',         2.50, 1, 1, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),'/uploads/REST-1/cola.jpg'),
(@restaurant_id, @cat_drinks_id, 'Orange Juice', 'Fresh juice',       3.50, 1, 1, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6),'/uploads/REST-1/Orange Juice.jpg');

-- Meals
INSERT INTO menu_items (
  restaurant_id, menu_category_id, name, description, price,
  is_available, is_active, display_order, created_at, updated_at, image_url
)
VALUES
(@restaurant_id, @cat_meals_id, 'Chicken Shawarma', 'With fries', 8.90, 1, 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Chicken Shawarma.jpg'),
(@restaurant_id, @cat_meals_id, 'Beef Burger',      '200g beef',  9.90, 1, 1, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Beef Burger.jpg'),
(@restaurant_id, @cat_meals_id, 'Falafel Plate',    '6 pieces',   7.50, 1, 1, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Falafel Plate.jpg');

-- Desserts
INSERT INTO menu_items (
  restaurant_id, menu_category_id, name, description, price,
  is_available, is_active, display_order, created_at, updated_at, image_url
)
VALUES
(@restaurant_id, @cat_desserts_id, 'Baklava',   'Pistachio', 4.50, 1, 1, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Baklava.jpg'),
(@restaurant_id, @cat_desserts_id, 'Vanilla Ice Cream', '2 scoops',  3.90, 1, 1, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Vanilla Ice Cream.jpg'),
(@restaurant_id, @cat_desserts_id, 'Kunafa',    'Hot dessert',5.50, 1, 1, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), '/uploads/REST-1/Kunafa.jpg');

-- =====================================
-- (E) Seed 10 restaurant tables with UUID qr_token
-- =====================================
INSERT INTO restaurant_tables (restaurant_id, table_number, qr_token, is_active, created_at, updated_at)
VALUES
(@restaurant_id, 1,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 2,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 3,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 4,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 5,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 6,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 7,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 8,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 9,  UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
(@restaurant_id, 10, UUID(), 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- =====================================
-- (F) Return IDs (and category ids)
-- =====================================
SELECT
  @restaurant_id    AS restaurant_id,
  @staff_user_id    AS staff_user_id,
  @cat_drinks_id    AS drinks_category_id,
  @cat_meals_id     AS meals_category_id,
  @cat_desserts_id  AS desserts_category_id;

COMMIT;


