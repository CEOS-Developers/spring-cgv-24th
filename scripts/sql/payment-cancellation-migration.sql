-- Run once after payment-history-migration.sql on the local test DB.
-- This script adds columns/tables only; existing purchases are preserved.
USE cgv_payment_test;

ALTER TABLE payment
    MODIFY COLUMN status ENUM('READY','PROCESSING','DONE','PARTIAL_CANCELED','FAILED','UNKNOWN','CANCELED','EXPIRED') NOT NULL,
    ADD COLUMN balance_amount BIGINT NOT NULL DEFAULT 0;

-- Earlier code did not support refunds. Only DONE payments have an approved balance.
UPDATE payment SET balance_amount = amount WHERE status = 'DONE' AND approved_at IS NOT NULL;

ALTER TABLE payment ADD CONSTRAINT chk_payment_balance CHECK (balance_amount >= 0 AND balance_amount <= amount);

CREATE TABLE payment_cancellation (
    cancellation_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    pg_idempotency_key VARCHAR(36) NOT NULL,
    requested_amount BIGINT NULL,
    cancel_amount BIGINT NOT NULL,
    balance_before BIGINT NOT NULL,
    cancel_reason VARCHAR(200) NOT NULL,
    status ENUM('PROCESSING','DONE','FAILED','UNKNOWN') NOT NULL,
    transaction_key VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    canceled_at DATETIME(6) NULL,
    PRIMARY KEY (cancellation_id),
    CONSTRAINT uk_payment_cancel_request UNIQUE (payment_id, request_id),
    CONSTRAINT uk_payment_cancel_transaction UNIQUE (transaction_key),
    CONSTRAINT uk_payment_cancel_idempotency UNIQUE (pg_idempotency_key),
    CONSTRAINT fk_cancellation_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id),
    CONSTRAINT chk_cancel_amount CHECK (cancel_amount > 0 AND cancel_amount <= balance_before)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
