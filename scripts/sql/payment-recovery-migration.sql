-- 기존 데이터를 유지하는 MySQL 환경에서 애플리케이션 배포 전에 한 번 적용한다.
ALTER TABLE payment
    ADD COLUMN next_recovery_at DATETIME(6) NULL,
    ADD COLUMN recovery_attempts INT NOT NULL DEFAULT 0,
    ADD INDEX idx_payment_recovery (status, next_recovery_at, payment_id);

ALTER TABLE payment_cancellation
    ADD COLUMN next_recovery_at DATETIME(6) NULL,
    ADD COLUMN recovery_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN rejected_by_pg BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_cancellation_recovery (status, next_recovery_at, cancellation_id);
