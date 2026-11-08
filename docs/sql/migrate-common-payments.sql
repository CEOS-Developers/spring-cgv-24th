-- 기존 매점 결제가 저장된 DB에서 애플리케이션을 중지하고 백업한 뒤 한 번 실행한다.
-- migrate-purchase-orders.sql / migrate-payment-service.sql 단계가 적용된 스키마를 대상으로 한다.
-- 신규 DB에는 실행하지 않는다. 이 파일은 자동 실행되지 않으며 재실행용이 아니다.
-- MySQL DDL은 전체가 하나의 트랜잭션으로 롤백되지 않으므로 실행 전 현재 컬럼/제약을 확인한다.

-- 아래 조회 결과가 없어야 한다. 결과가 있으면 데이터 원인을 확인한 뒤 이관한다.
SELECT pay.id AS inconsistent_payment_id
FROM payment pay
LEFT JOIN purchase p ON p.id = pay.purchase_id
WHERE p.id IS NULL OR p.order_id IS NULL OR p.user_id IS NULL
   OR p.expires_at IS NULL OR pay.total_amount <> p.total_amount;

ALTER TABLE payment
    ADD COLUMN target_type VARCHAR(20) NULL,
    ADD COLUMN order_id VARCHAR(64) NULL,
    ADD COLUMN payer_id BIGINT NULL,
    ADD COLUMN expires_at DATETIME(6) NULL,
    ADD COLUMN reservation_id BIGINT NULL;

-- 기존 주문번호를 그대로 사용해 PG 멱등키와 작업 요청 해시의 의미를 보존한다.
-- 금액/결제 상태/작업 이력은 변경하지 않는다.
UPDATE payment pay
JOIN purchase p ON p.id = pay.purchase_id
SET pay.target_type = 'PURCHASE',
    pay.order_id = p.order_id,
    pay.payer_id = p.user_id,
    pay.expires_at = p.expires_at;

ALTER TABLE payment
    MODIFY COLUMN purchase_id BIGINT NULL,
    MODIFY COLUMN target_type VARCHAR(20) NOT NULL,
    MODIFY COLUMN order_id VARCHAR(64) NOT NULL,
    MODIFY COLUMN payer_id BIGINT NOT NULL,
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT uk_payment_order UNIQUE (order_id),
    ADD CONSTRAINT uk_payment_reservation UNIQUE (reservation_id),
    ADD CONSTRAINT fk_payment_payer FOREIGN KEY (payer_id) REFERENCES users(id),
    ADD CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES reservation(id),
    ADD CONSTRAINT ck_payment_target CHECK (
        (target_type = 'PURCHASE' AND purchase_id IS NOT NULL AND reservation_id IS NULL)
        OR (target_type = 'RESERVATION' AND reservation_id IS NOT NULL AND purchase_id IS NULL)
    );
