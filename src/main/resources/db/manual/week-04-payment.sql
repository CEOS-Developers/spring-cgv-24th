-- Week 4 payment upgrade for MySQL 8.4 / InnoDB.
-- Apply once, with the application stopped, after backing up and comparing SHOW CREATE TABLE
-- reservations / reserved_seats with the week-3 baseline. MySQL DDL commits implicitly.
-- Baseline: existing reservation status ENUM and no reserved_seats.canceled_at.
-- Preserve the order of existing ENUM values; append PAYMENT_PENDING.
-- Do not use this as a reset script. On a partial failure inspect the schema before resuming.
-- Snapshot user/reservation/seat IDs and active_operation_id intentionally have no FK:
-- the JPA model stores scalar snapshots; only actual entity relationships have FK constraints.
-- There is no automatic DROP rollback: it would destroy payment/refund audit records.
-- Correct an applied change with a reviewed forward migration or restore the verified backup.

-- Retain the actual baseline ENUM order rather than assume Hibernate's declaration order.
SET @payment_reservation_status_type = (
    SELECT column_type FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'reservations' AND column_name = 'status'
);
SET @payment_reservation_status_upgrade = CONCAT(
    'ALTER TABLE reservations MODIFY COLUMN status ',
    LEFT(@payment_reservation_status_type, CHAR_LENGTH(@payment_reservation_status_type) - 1),
    ',''PAYMENT_PENDING'') NOT NULL'
);
PREPARE payment_reservation_status_statement FROM @payment_reservation_status_upgrade;
EXECUTE payment_reservation_status_statement;
DEALLOCATE PREPARE payment_reservation_status_statement;

ALTER TABLE reserved_seats
    ADD COLUMN canceled_at DATETIME(6) NULL;

CREATE TABLE payment_order (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    reservation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    order_name VARCHAR(255) NOT NULL,
    seat_price BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_order_order_id UNIQUE (order_id),
    CONSTRAINT uk_payment_order_reservation_id UNIQUE (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_order_seat (
    payment_order_id BIGINT NOT NULL,
    seat_index INT NOT NULL,
    reserved_seat_id BIGINT NOT NULL,
    seat_row VARCHAR(255) NOT NULL,
    seat_number INT NOT NULL,
    canceled_at DATETIME(6) NULL,
    PRIMARY KEY (payment_order_id, seat_index),
    CONSTRAINT fk_payment_order_seat_order FOREIGN KEY (payment_order_id) REFERENCES payment_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    payment_order_id BIGINT NOT NULL,
    payment_key VARCHAR(200) NULL,
    status ENUM('READY','PROCESSING','DONE','PARTIALLY_CANCELED','CANCELED','FAILED','COMPENSATING') NOT NULL,
    balance_amount BIGINT NOT NULL,
    active_operation_id BIGINT NULL,
    method VARCHAR(255) NULL,
    approved_at DATETIME(6) NULL,
    receipt_url VARCHAR(2000) NULL,
    cancellations_json TEXT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_order UNIQUE (payment_order_id),
    CONSTRAINT uk_payment_key UNIQUE (payment_key),
    CONSTRAINT fk_payment_order FOREIGN KEY (payment_order_id) REFERENCES payment_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_operation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    user_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    type ENUM('CONFIRM','CANCEL') NOT NULL,
    status ENUM('PROCESSING','UNKNOWN','COMPENSATING','SUCCEEDED','FAILED') NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    gateway_key VARCHAR(64) NOT NULL,
    compensation_key VARCHAR(64) NULL,
    request_amount BIGINT NOT NULL,
    previous_balance BIGINT NOT NULL,
    seat_ids_json TEXT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    result_json TEXT NULL,
    failure_code VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_operation_request UNIQUE (user_id, type, request_key),
    CONSTRAINT uk_payment_operation_gateway UNIQUE (gateway_key),
    CONSTRAINT uk_payment_operation_compensation UNIQUE (compensation_key),
    KEY idx_payment_operation_payment (payment_id),
    CONSTRAINT fk_payment_operation_payment FOREIGN KEY (payment_id) REFERENCES payment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
