-- Run once after the app has created the schema in the dedicated local cgv_load_test database.
CREATE TEMPORARY TABLE load_fixture_guard (
    database_name VARBINARY(64) NOT NULL,
    empty_tables BOOLEAN NOT NULL,
    CONSTRAINT load_fixture_requires_cgv_load_test CHECK (database_name = 'cgv_load_test'),
    CONSTRAINT load_fixture_requires_empty_tables CHECK (empty_tables = TRUE)
);

INSERT INTO load_fixture_guard (database_name, empty_tables)
VALUES (
    DATABASE(),
    NOT EXISTS (SELECT 1 FROM movies)
        AND NOT EXISTS (SELECT 1 FROM cinemas)
        AND NOT EXISTS (SELECT 1 FROM users)
);

CREATE TEMPORARY TABLE load_fixture_numbers (n INT PRIMARY KEY);

INSERT INTO load_fixture_numbers (n)
WITH RECURSIVE numbers (n) AS (
    SELECT 1
    UNION ALL
    SELECT n + 1 FROM numbers WHERE n < 250
)
SELECT n FROM numbers;

START TRANSACTION;

INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
SELECT
    10000 + n,
    CONCAT('Load test movie ', LPAD(n, 3, '0')),
    'A local fixture for measuring movie list serialization and response latency.',
    90 + MOD(n, 60),
    DATE_ADD('2025-01-01', INTERVAL n DAY),
    'ALL',
    IF(n <= 200, 'PUBLIC', 'HIDDEN')
FROM load_fixture_numbers;

INSERT INTO cinemas (cinema_id, name, address)
VALUES (10001, 'Load test cinema', 'Local test fixture');

INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
VALUES (10001, 10001, 'GENERAL', 10, 20);

INSERT INTO screenings (screening_id, movie_id, screen_id, start_at)
VALUES (10001, 10001, 10001, '2099-01-01 13:00:00');

INSERT INTO users (user_id, name, email, role)
SELECT 10000 + n, CONCAT('Load test user ', n), CONCAT('load-test-', n, '@example.invalid'), 'USER'
FROM load_fixture_numbers
WHERE n <= 40;

INSERT INTO reservations (reservation_id, user_id, screening_id, status, created_at, updated_at)
SELECT 10000 + n, 10000 + n, 10001, 'RESERVED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM load_fixture_numbers
WHERE n <= 40;

-- Ten rows (A through J), 20 seats per row, with every fifth seat reserved.
INSERT INTO screening_seats (screening_seat_id, screening_id, seat_row, seat_number, current_reservation_id)
SELECT
    10000 + n,
    10001,
    CHAR(ASCII('A') + ((n - 1) DIV 20)),
    MOD(n - 1, 20) + 1,
    IF(MOD(n, 5) = 0, 10000 + (n DIV 5), NULL)
FROM load_fixture_numbers
WHERE n <= 200;

INSERT INTO reserved_seats (reservation_id, screening_seat_id, seat_row, seat_number)
SELECT current_reservation_id, screening_seat_id, seat_row, seat_number
FROM screening_seats
WHERE screening_id = 10001 AND current_reservation_id IS NOT NULL;

COMMIT;

SELECT visibility, COUNT(*) AS movies
FROM movies
GROUP BY visibility
ORDER BY visibility;

SELECT s.screening_id, COUNT(*) AS seats, COUNT(r.reservation_id) AS reserved_seats
FROM screening_seats s
LEFT JOIN reservations r ON r.reservation_id = s.current_reservation_id AND r.status = 'RESERVED'
WHERE s.screening_id = 10001
GROUP BY s.screening_id;
