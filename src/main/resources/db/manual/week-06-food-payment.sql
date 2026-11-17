-- MySQL 8.4 / InnoDB. Apply once to the week-4 payment schema before running this code.
-- Stop the application, back up the database and compare both tables with SHOW CREATE TABLE.
-- Existing food orders keep NULL checkout fields. Movie payment rows retain their snapshots.
-- Each ALTER commits independently. On a partial failure, inspect the schema before resuming.

ALTER TABLE food_orders
    ADD COLUMN checkout_request_key VARCHAR(36) NULL,
    ADD COLUMN payment_status VARCHAR(24) NULL,
    ADD COLUMN hold_expires_at DATETIME(6) NULL,
    ADD CONSTRAINT uk_food_order_checkout_key UNIQUE (user_id, checkout_request_key),
    ADD INDEX idx_food_order_checkout_expiry (payment_status, hold_expires_at, order_id);

ALTER TABLE payment_order
    MODIFY COLUMN reservation_id BIGINT NULL,
    MODIFY COLUMN seat_price BIGINT NULL,
    ADD COLUMN food_order_id BIGINT NULL,
    ADD CONSTRAINT uk_payment_order_food_order_id UNIQUE (food_order_id),
    ADD CONSTRAINT chk_payment_order_target CHECK (
        (reservation_id IS NOT NULL AND food_order_id IS NULL AND seat_price IS NOT NULL AND seat_price > 0)
        OR (reservation_id IS NULL AND food_order_id IS NOT NULL AND seat_price IS NULL AND total_amount > 0)
    );
