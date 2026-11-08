-- 공통 결제 구조(migrate-common-payments.sql)가 있는 기존 DB에서 앱 중지 후 한 번 적용한다.
-- 새 DB에는 실행하지 않는다. 실제 DB에 자동 적용되지 않는다.
ALTER TABLE reservation
    MODIFY COLUMN status VARCHAR(20) NOT NULL,
    ADD COLUMN confirmed_at DATETIME(6) NULL;

-- 기존 CONFIRMED/CANCELLED 예매는 그대로 보존한다.
-- 토스 승인 기록이나 확정 시각을 추측해서 생성하지 않는다.
