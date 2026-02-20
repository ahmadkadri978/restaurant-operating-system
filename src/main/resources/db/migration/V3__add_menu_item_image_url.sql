-- Restaurant QR System - MySQL 8+ Schema- V3__menu_item_image.sql
ALTER TABLE menu_items
ADD COLUMN image_url VARCHAR(500) NULL;
