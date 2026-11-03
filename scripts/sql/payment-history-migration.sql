-- Run once on the existing local payment-test database before restarting.
USE cgv_payment_test;
ALTER TABLE payment
    ADD COLUMN order_name VARCHAR(100) NULL,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'KRW',
    ADD COLUMN payment_method VARCHAR(100) NULL;

-- The previous implementation used these fixed names and supported only KRW.
UPDATE payment SET order_name = CASE target_type
    WHEN 'RESERVATION' THEN '영화 티켓 예매'
    WHEN 'STORE_ORDER' THEN '매점 상품 주문'
END WHERE order_name IS NULL;

ALTER TABLE payment MODIFY COLUMN order_name VARCHAR(100) NOT NULL;
CREATE INDEX idx_payment_user_status_approved ON payment(user_id, status, approved_at, payment_id);
-- Older payment methods were never stored: leave NULL instead of inventing data.
