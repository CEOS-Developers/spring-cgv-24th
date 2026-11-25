-- payment-recovery-migration.sql이 적용된 기존 DB에 배포 전에 한 번 적용한다.
ALTER TABLE payment ADD COLUMN rejected_by_pg BOOLEAN NOT NULL DEFAULT FALSE;
