-- 실험 1. 예매 만료 조회 인덱스 실험용 데이터 (로컬 전용 DB, 애플리케이션과 연결하지 않는다)
DROP DATABASE IF EXISTS cgv_index_lab;
CREATE DATABASE cgv_index_lab CHARACTER SET utf8mb4;
USE cgv_index_lab;

-- 실제 스키마(ddl-auto로 생성된 reservation)와 같은 컬럼·인덱스 구성 (FK 제약만 제외)
CREATE TABLE reservation (
  total_price int NOT NULL,
  id bigint NOT NULL AUTO_INCREMENT,
  reserved_at datetime(6) NOT NULL,
  schedule_id bigint NOT NULL,
  user_id bigint NOT NULL,
  seat_summary varchar(255) NOT NULL,
  status varchar(255) NOT NULL,
  expires_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_schedule_id (schedule_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB;

SET SESSION cte_max_recursion_depth = 1000000;
-- 비교 기준 시각 고정
SET @now = TIMESTAMP('2026-11-25 12:00:00');

-- 과거 180일 동안 쌓인 예매 200,000건
-- 상태 분포: RESERVED 70% / EXPIRED 20% / CANCELLED 10%, 가장 오래된 5%는 expires_at 도입 전 데이터(NULL)
INSERT INTO reservation (total_price, reserved_at, schedule_id, user_id, seat_summary, status, expires_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 200000)
SELECT 15000 * (1 + MOD(n, 4)),
       @now - INTERVAL 180 DAY + INTERVAL FLOOR(n * 77.76) SECOND - INTERVAL 1 HOUR,
       1 + MOD(n * 7, 3000),
       1 + MOD(n * 13, 20000),
       'A1',
       CASE WHEN MOD(n, 10) < 7 THEN 'RESERVED' WHEN MOD(n, 10) < 9 THEN 'EXPIRED' ELSE 'CANCELLED' END,
       CASE WHEN n <= 10000 THEN NULL
            ELSE @now - INTERVAL 180 DAY + INTERVAL FLOOR(n * 77.76) SECOND - INTERVAL 55 MINUTE END
FROM seq;

-- 현재 결제 대기 중인 예매 300건: 최근 10분 사이 생성, 선점 5분 (절반가량은 만료 대상)
INSERT INTO reservation (total_price, reserved_at, schedule_id, user_id, seat_summary, status, expires_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 300)
SELECT 15000, @now - INTERVAL n * 2 SECOND, 1 + MOD(n, 3000), 1 + MOD(n * 13, 20000), 'B1',
       'PENDING_PAYMENT', @now - INTERVAL n * 2 SECOND + INTERVAL 5 MINUTE
FROM seq;

-- 결제 진행 중 50건
INSERT INTO reservation (total_price, reserved_at, schedule_id, user_id, seat_summary, status, expires_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50)
SELECT 15000, @now - INTERVAL n * 3 SECOND, 1 + MOD(n, 3000), 1 + MOD(n * 17, 20000), 'C1',
       'PAYING', @now - INTERVAL n * 3 SECOND + INTERVAL 5 MINUTE
FROM seq;

ANALYZE TABLE reservation;

SELECT status, COUNT(*) cnt, SUM(expires_at <= @now) expired_cnt FROM reservation GROUP BY status;
