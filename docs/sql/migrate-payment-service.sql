-- 이전 단계의 payment/payment_operation 테이블이 있는 DB에서 앱 재시작 전에 한 번 실행한다.
-- 신규 DB에는 실행하지 않는다. 실제 DB에는 자동 적용되지 않는다.
ALTER TABLE payment MODIFY COLUMN status VARCHAR(30) NOT NULL;

ALTER TABLE payment_operation
    ADD COLUMN full_cancellation BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN before_canceled_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1,
    ADD COLUMN result_payment_status VARCHAR(30) NULL,
    ADD COLUMN result_canceled_amount BIGINT NULL;

UPDATE payment_operation SET last_attempt_at = requested_at;

ALTER TABLE payment_operation
    MODIFY COLUMN last_attempt_at TIMESTAMP(6) NOT NULL,
    ADD CONSTRAINT uk_payment_operation_transaction UNIQUE (pg_transaction_key);

-- 과거 수동 생성 이력의 성공 응답은 현재 결제 상태로 추정해 채우지 않는다.
-- 결과 스냅샷이 없는 성공 이력의 재요청은 서비스에서 수동 확인 대상으로 처리한다.
