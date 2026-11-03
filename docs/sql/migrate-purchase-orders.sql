-- 기존 purchase 스키마에 데이터가 있는 환경에서 애플리케이션을 중지한 뒤 한 번 실행한다.
-- 새 DB는 JPA가 스키마를 생성하므로 이 스크립트를 실행하지 않는다.
-- 기존 구매는 토스 승인 기록과 구분하기 위해 LEGACY_COMPLETED로 이관한다.

ALTER TABLE purchase
    ADD COLUMN order_id VARCHAR(64) NULL,
    ADD COLUMN total_amount BIGINT NULL,
    ADD COLUMN status VARCHAR(30) NULL,
    ADD COLUMN expires_at DATETIME(6) NULL;

UPDATE purchase p
LEFT JOIN (
    SELECT purchase_id, SUM(CAST(unit_price AS SIGNED) * quantity) AS total_amount
    FROM purchase_item
    GROUP BY purchase_id
) items ON items.purchase_id = p.id
SET p.order_id = CONCAT('cgv_legacy_', UUID()),
    p.total_amount = COALESCE(items.total_amount, 0),
    p.status = 'LEGACY_COMPLETED',
    p.expires_at = DATE_ADD(p.purchased_at, INTERVAL 30 MINUTE);

ALTER TABLE purchase
    MODIFY COLUMN order_id VARCHAR(64) NOT NULL,
    MODIFY COLUMN total_amount BIGINT NOT NULL,
    MODIFY COLUMN status VARCHAR(30) NOT NULL,
    MODIFY COLUMN expires_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT uk_purchase_order_id UNIQUE (order_id);
