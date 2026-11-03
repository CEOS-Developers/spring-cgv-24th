-- Run once on the local payment-test database BEFORE restarting the new application.
-- Historical orders have no reliable payment state: preserve their consumed stock
-- by marking them PAID (legacy completed orders), never recreate a pending hold.
USE cgv_payment_test;

ALTER TABLE store_order
    ADD COLUMN status ENUM('PENDING', 'PAID', 'CANCELED', 'EXPIRED') NOT NULL DEFAULT 'PAID',
    ADD COLUMN expires_at DATETIME(6) NULL,
    ADD COLUMN payment_processing BIT NOT NULL DEFAULT b'0';

ALTER TABLE order_item ADD COLUMN unit_price INT NULL;
-- Old unit prices were not recorded; only the current menu price is recoverable.
UPDATE order_item oi JOIN menu m ON m.menu_id = oi.menu_id SET oi.unit_price = m.menu_price;
ALTER TABLE order_item MODIFY COLUMN unit_price INT NOT NULL;

-- Resolve duplicate (theater_id, menu_id) inventory rows before applying this constraint.
ALTER TABLE store_inventory ADD CONSTRAINT uk_inventory_theater_menu UNIQUE (theater_id, menu_id);
