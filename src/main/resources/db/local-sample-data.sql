-- local 프로필 전용 샘플 데이터. 결제 테스트 페이지(/payment-test.html)에서 고를 회차·상품이 있어야 한다.
-- ddl-auto: create라 부팅마다 테이블이 새로 만들어지므로 매번 같은 id로 다시 들어간다.
-- 회차는 "내일"로 둔다. 지난 회차는 선점할 수 없고, 오늘 회차는 실행 시각에 따라 이미 지났을 수 있다.

INSERT INTO branch (branch_id, name, address, region, status, description, image_url, created_at, updated_at)
VALUES (1, 'CGV 강남', '서울특별시 강남구 강남대로 438', 'SEOUL', 'OPEN', '결제 테스트용 샘플 지점', NULL, NOW(), NOW());

INSERT INTO theater (theater_id, branch_id, theater_type, name, created_at, updated_at)
VALUES (1, 1, 'STANDARD', '1관', NOW(), NOW());

INSERT INTO movie (movie_id, title, director, genre, running_time, release_date, age_rating, created_at, updated_at)
VALUES (1, '범죄도시4', '허명행', '액션', 109, '2024-04-24', '15세', NOW(), NOW());

INSERT INTO screening (screening_id, theater_id, movie_id, start_at, end_at, price, created_at, updated_at)
VALUES (1, 1, 1, TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '10:00:00'), TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '11:49:00'), 14000, NOW(), NOW()),
       (2, 1, 1, TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '19:00:00'), TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '20:49:00'), 15000, NOW(), NOW());

INSERT INTO product (product_id, name, price, created_at, updated_at)
VALUES (1, '팝콘(L)', 6500, NOW(), NOW()),
       (2, '콜라(M)', 3000, NOW(), NOW()),
       (3, '나초', 5500, NOW(), NOW());

-- 나초는 재고 1이라 판매 가능 수량이 0이다(품절 표시 확인용).
INSERT INTO stock (stock_id, branch_id, product_id, quantity, created_at, updated_at)
VALUES (1, 1, 1, 50, NOW(), NOW()),
       (2, 1, 2, 50, NOW(), NOW()),
       (3, 1, 3, 1, NOW(), NOW());
