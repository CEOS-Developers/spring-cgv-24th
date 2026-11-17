-- loadtest 프로필 전용 부하 테스트 시드. ddl-auto: create라 앱을 다시 띄우면 처음 상태로 돌아간다(시나리오마다 초기화).
-- 회차 시각은 NOW() 기준 하루 뒤로 잡는다. DB 시간대가 앱과 달라도, 자정을 넘겨도 지난 회차가 되지 않는다.
-- id를 1001부터 고정하는 이유: k6 스크립트가 같은 번호를 상수로 쓴다(loadtest/scripts/lib/common.js).

-- 부하 전용 계정 loadtest0001~loadtest1000, 평문 비밀번호 loadtest1! (로컬 부하 테스트에서만 쓰는 값)
-- 해시는 BCryptPasswordEncoder(강도 10)로 한 번 만들어 모두 같은 값을 쓴다. 로그인 비용은 해시마다 같다.
INSERT INTO users (user_id, login_id, password, name, birth_date, email, phone_number, role, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 1000)
SELECT 1000 + n,
       CONCAT('loadtest', LPAD(n, 4, '0')),
       '$2a$10$s3bedcUmdxJ1SEQpQObnLOnoe3yoRi6e3nZIfKjQKXfrLzyDATbK2',
       '부하테스트',
       '2000-01-01',
       CONCAT('loadtest', LPAD(n, 4, '0'), '@test.com'),
       CONCAT('010', LPAD(n, 8, '0')),
       'USER', NOW(), NOW()
FROM seq;

INSERT INTO branch (branch_id, name, address, region, status, description, image_url, created_at, updated_at)
VALUES (1001, 'CGV 부하테스트', '서울특별시 강남구 강남대로 438', 'SEOUL', 'OPEN', '부하 테스트용 지점', NULL, NOW(), NOW());

INSERT INTO movie (movie_id, title, director, genre, running_time, release_date, age_rating, created_at, updated_at)
VALUES (1001, '부하테스트 개봉작', '감독', '액션', 120, CURDATE(), '15세', NOW(), NOW());

-- 모두 IMAX(12×22=264석). 좌석이 가장 많아 인기 회차가 늦게 매진된다.
INSERT INTO theater (theater_id, branch_id, theater_type, name, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 11)
SELECT 1000 + n, 1001, 'IMAX', CONCAT('IMAX ', n, '관'), NOW(), NOW()
FROM seq;

-- 1001~1005 인기 회차, 1006~1100 일반 회차. 상영관 1001~1010에 10회차씩, 140분 간격.
INSERT INTO screening (screening_id, theater_id, movie_id, start_at, end_at, price, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 100)
SELECT 1000 + n,
       1001 + MOD(n - 1, 10),
       1001,
       NOW() + INTERVAL 1 DAY + INTERVAL ((n - 1) DIV 10) * 140 MINUTE,
       NOW() + INTERVAL 1 DAY + INTERVAL ((n - 1) DIV 10) * 140 + 120 MINUTE,
       15000, NOW(), NOW()
FROM seq;

-- 좌석 배치도 조회용 "반쯤 찬" 회차. 조회 쿼리가 실제 크기의 점유 좌석을 다루게 한다.
INSERT INTO screening (screening_id, theater_id, movie_id, start_at, end_at, price, created_at, updated_at)
VALUES (2001, 1011, 1001, NOW() + INTERVAL 1 DAY, NOW() + INTERVAL 1 DAY + INTERVAL 120 MINUTE, 15000, NOW(), NOW());

INSERT INTO reservation (reservation_id, user_id, screening_id, status, expires_at, confirmed_at, cancelled_at, created_at, updated_at)
VALUES (1001, 1001, 2001, 'RESERVED', NOW(), NOW(), NULL, NOW(), NOW());

-- 1~6행 전체(6×22=132석)
INSERT INTO reservation_seat (reservation_id, screening_id, row_num, col_num, audience_type, paid_price, release_key,
                              payment_cancel_id, cancelled_at, created_at, updated_at)
WITH RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 6),
               c (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM c WHERE n < 22)
SELECT 1001, 2001, r.n, c.n, 'ADULT', 15000, 0, NULL, NULL, NOW(), NOW()
FROM r CROSS JOIN c;
