-- 성능 최적화 실험용 대량 데이터. 앱 시드(회차 100개)로는 옵티마이저가 풀 스캔을 골라도 빨라 실행 계획 차이가 드러나지 않는다.
-- 사용법: 앱을 한 번 띄워 테이블을 만든 뒤(ddl-auto: create) 이 파일을 Workbench에서 그 스키마에 실행한다.
--        앱을 다시 띄우면 테이블이 새로 만들어져 데이터가 사라지므로 그때마다 다시 실행한다.
-- id는 100001부터 쓴다. 앱 시드(1~, 1001~)와 겹치지 않는다.
--
-- 만들어지는 양
--   영화 50 / 지점 30 / 상영관 240 (지점당 8, 종류 4가지 섞음) / 사용자 1,000
--   회차 129,600 (상영관 240 × 90일[오늘-30일 ~ 오늘+59일] × 하루 6회)
--   예매 518,400 (회차마다 4건) / 예매 좌석 1,036,800 (예매마다 2석)
--     예매 상태: 대부분 RESERVED, 10회차에 1번꼴 PENDING(만료된 선점), 7회차에 1번꼴 CANCELLED(좌석 해제됨)
--   좌석 선점 실험용 회차 900001: IMAX(264석) 중 220석 점유 (2석짜리 예매 110건)

SET SESSION cte_max_recursion_depth = 10000;

INSERT INTO movie (movie_id, title, director, genre, running_time, release_date, age_rating, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 50)
SELECT 100000 + n, CONCAT('실험 영화 ', n), '감독', '드라마', 120, CURDATE() - INTERVAL n DAY, '15세', NOW(), NOW()
FROM seq;

INSERT INTO branch (branch_id, name, address, region, status, description, image_url, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 30)
SELECT 100000 + n, CONCAT('CGV 실험', LPAD(n, 2, '0')), '서울특별시', 'SEOUL', 'OPEN', '실험용 지점', NULL, NOW(), NOW()
FROM seq;

INSERT INTO theater (theater_id, branch_id, theater_type, name, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 240)
SELECT 100000 + n,
       100001 + (n - 1) DIV 8,
       ELT(MOD(n - 1, 4) + 1, 'STANDARD', 'IMAX', 'FOUR_DX', 'SCREEN_X'),
       CONCAT(MOD(n - 1, 8) + 1, '관'),
       NOW(), NOW()
FROM seq;

INSERT INTO users (user_id, login_id, password, name, birth_date, email, phone_number, role, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT 100000 + n, CONCAT('perf', LPAD(n, 4, '0')), 'not-a-real-hash', '실험', '2000-01-01',
       CONCAT('perf', n, '@test.com'), CONCAT('011', LPAD(n, 8, '0')), 'USER', NOW(), NOW()
FROM seq;

-- 회차: 상영관 × 90일 × 하루 6회(09:00부터 150분 간격). 영화는 상영관·날짜·회차에 따라 돌려 배정한다.
INSERT INTO screening (screening_id, theater_id, movie_id, start_at, end_at, price, created_at, updated_at)
WITH RECURSIVE d (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM d WHERE n < 89),
               s (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM s WHERE n < 5)
SELECT 100000 + (t.theater_id - 100001) * 540 + d.n * 6 + s.n + 1,
       t.theater_id,
       100001 + MOD(t.theater_id + d.n + s.n, 50),
       TIMESTAMP(CURDATE() - INTERVAL 30 DAY + INTERVAL d.n DAY, '09:00:00') + INTERVAL s.n * 150 MINUTE,
       TIMESTAMP(CURDATE() - INTERVAL 30 DAY + INTERVAL d.n DAY, '09:00:00') + INTERVAL s.n * 150 + 120 MINUTE,
       15000, NOW(), NOW()
FROM theater t CROSS JOIN d CROSS JOIN s
WHERE t.theater_id > 100000;

-- 예매: 회차마다 4건. id = 회차 id × 4 + k
INSERT INTO reservation (reservation_id, user_id, screening_id, status, expires_at, confirmed_at, cancelled_at, created_at, updated_at)
WITH RECURSIVE k (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM k WHERE n < 3)
SELECT sc.screening_id * 4 + k.n,
       100001 + MOD(sc.screening_id * 4 + k.n, 1000),
       sc.screening_id,
       CASE WHEN k.n = 3 AND MOD(sc.screening_id, 10) = 0 THEN 'PENDING'
            WHEN k.n = 2 AND MOD(sc.screening_id, 7) = 0 THEN 'CANCELLED'
            ELSE 'RESERVED' END,
       CASE WHEN k.n = 3 AND MOD(sc.screening_id, 10) = 0 THEN NOW() - INTERVAL 1 HOUR ELSE NOW() END,
       CASE WHEN (k.n = 3 AND MOD(sc.screening_id, 10) = 0) OR (k.n = 2 AND MOD(sc.screening_id, 7) = 0)
            THEN NULL ELSE NOW() END,
       CASE WHEN k.n = 2 AND MOD(sc.screening_id, 7) = 0 THEN NOW() ELSE NULL END,
       NOW(), NOW()
FROM screening sc CROSS JOIN k
WHERE sc.screening_id BETWEEN 100001 AND 899999;

-- 예매 좌석: 예매마다 2석(k+1행 1·2열). 취소된 예매의 좌석은 해제 상태(release_key = 예매 id)다.
-- 한 번에 100만 행을 넣으면 Workbench 기본 읽기 제한(30초)에 걸려 끊긴다. 예매 id 범위로 4번에 나눈다(각 약 26만 행).
INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
SELECT r.reservation_id, r.screening_id, MOD(r.reservation_id, 4) + 1, c.n, 'ADULT', 15000,
       CASE WHEN r.status = 'CANCELLED' THEN r.reservation_id ELSE 0 END,
       NULL, IF(r.status = 'CANCELLED', NOW(), NULL), NOW(), NOW()
FROM reservation r CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) c
WHERE r.reservation_id BETWEEN 400004 AND 529999;

INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
SELECT r.reservation_id, r.screening_id, MOD(r.reservation_id, 4) + 1, c.n, 'ADULT', 15000,
       CASE WHEN r.status = 'CANCELLED' THEN r.reservation_id ELSE 0 END,
       NULL, IF(r.status = 'CANCELLED', NOW(), NULL), NOW(), NOW()
FROM reservation r CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) c
WHERE r.reservation_id BETWEEN 530000 AND 659999;

INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
SELECT r.reservation_id, r.screening_id, MOD(r.reservation_id, 4) + 1, c.n, 'ADULT', 15000,
       CASE WHEN r.status = 'CANCELLED' THEN r.reservation_id ELSE 0 END,
       NULL, IF(r.status = 'CANCELLED', NOW(), NULL), NOW(), NOW()
FROM reservation r CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) c
WHERE r.reservation_id BETWEEN 660000 AND 789999;

INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
SELECT r.reservation_id, r.screening_id, MOD(r.reservation_id, 4) + 1, c.n, 'ADULT', 15000,
       CASE WHEN r.status = 'CANCELLED' THEN r.reservation_id ELSE 0 END,
       NULL, IF(r.status = 'CANCELLED', NOW(), NULL), NOW(), NOW()
FROM reservation r CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2) c
WHERE r.reservation_id BETWEEN 790000 AND 999999;

-- 좌석 선점 실험용 회차: 내일 21:00, IMAX 상영관(100002). 1~10행 전체(220석)가 2석짜리 예매 110건으로 점유돼 있다.
INSERT INTO screening (screening_id, theater_id, movie_id, start_at, end_at, price, created_at, updated_at)
VALUES (900001, 100002, 100001, TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '21:00:00'),
        TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '23:00:00'), 15000, NOW(), NOW());

INSERT INTO reservation (reservation_id, user_id, screening_id, status, expires_at, confirmed_at, cancelled_at, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 110)
SELECT 9000000 + n, 100001 + n, 900001, 'RESERVED', NOW(), NOW(), NULL, NOW(), NOW()
FROM seq;

INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 110),
               c (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM c WHERE n < 2)
SELECT 9000000 + seq.n, 900001, (seq.n - 1) DIV 11 + 1, MOD(seq.n - 1, 11) * 2 + c.n, 'ADULT', 15000, 0,
       NULL, NULL, NOW(), NOW()
FROM seq CROSS JOIN c;

-- 통계를 갱신해야 옵티마이저가 새 데이터 양을 보고 계획을 세운다.
ANALYZE TABLE movie, branch, theater, users, screening, reservation, reservation_seat;

-- 확인: 129601 / 518510 / 1037020 이면 정상 (local 프로필 앱 샘플 회차 2건이 있으면 회차는 129603)
SELECT (SELECT COUNT(*) FROM screening) AS screenings,
       (SELECT COUNT(*) FROM reservation) AS reservations,
       (SELECT COUNT(*) FROM reservation_seat) AS seats;
