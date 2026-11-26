-- ① 인덱스: 회차 검색(GET /api/screenings)에 screening (movie_id, start_at) 복합 인덱스
-- seed.sql을 넣은 스키마에서 위에서부터 차례로 실행한다. EXPLAIN ANALYZE는 두 번씩 돌려 두 번째 결과를 쓴다
-- (첫 번째는 디스크에서 페이지를 읽어 오는 시간이 섞인다).
--
-- 아래 쿼리는 ScreeningRepository.search가 만드는 SQL과 같은 모양이다. Connector/J는 기본이 클라이언트 측 바인딩이라
-- 파라미터가 리터럴로 들어간다. 그래서 선택 조건도 "100001 IS NULL OR ..."처럼 값이 박힌 채로 서버에 간다.

-- 0. 지금 인덱스 확인 (movie_id에는 외래 키가 만든 인덱스만 있어야 한다)
SHOW INDEX FROM screening;

-- 0-1. (인덱스 추가 커밋 이후 코드로 앱을 띄워 idx_screening_movie_start가 이미 있다면) 적용 전 상태로 되돌린다.
--      외래 키는 movie_id로 시작하는 인덱스가 하나는 있어야 해서, 단일 인덱스를 먼저 만들고 지운다.
-- CREATE INDEX idx_screening_movie_only ON screening (movie_id);
-- DROP INDEX idx_screening_movie_start ON screening;
-- ANALYZE TABLE screening;

-- ───────────────── 적용 전 ─────────────────

-- 1-A. 영화 + 날짜 (내일, 실험 영화 1)
EXPLAIN
SELECT s.*, m.*, t.*, b.*
FROM screening s
JOIN movie m ON m.movie_id = s.movie_id
JOIN theater t ON t.theater_id = s.theater_id
JOIN branch b ON b.branch_id = t.branch_id
WHERE s.start_at >= TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '00:00:00')
  AND s.start_at < TIMESTAMP(CURDATE() + INTERVAL 2 DAY, '00:00:00')
  AND (100001 IS NULL OR m.movie_id = 100001)
  AND (FALSE = FALSE OR b.branch_id IN (0))
  AND (NULL IS NULL OR t.theater_type = NULL)
ORDER BY b.name, s.start_at;

EXPLAIN ANALYZE
SELECT s.*, m.*, t.*, b.*
FROM screening s
JOIN movie m ON m.movie_id = s.movie_id
JOIN theater t ON t.theater_id = s.theater_id
JOIN branch b ON b.branch_id = t.branch_id
WHERE s.start_at >= TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '00:00:00')
  AND s.start_at < TIMESTAMP(CURDATE() + INTERVAL 2 DAY, '00:00:00')
  AND (100001 IS NULL OR m.movie_id = 100001)
  AND (FALSE = FALSE OR b.branch_id IN (0))
  AND (NULL IS NULL OR t.theater_type = NULL)
ORDER BY b.name, s.start_at;

-- 1-B. 날짜만 (영화 미선택)
EXPLAIN ANALYZE
SELECT s.*, m.*, t.*, b.*
FROM screening s
JOIN movie m ON m.movie_id = s.movie_id
JOIN theater t ON t.theater_id = s.theater_id
JOIN branch b ON b.branch_id = t.branch_id
WHERE s.start_at >= TIMESTAMP(CURDATE() + INTERVAL 1 DAY, '00:00:00')
  AND s.start_at < TIMESTAMP(CURDATE() + INTERVAL 2 DAY, '00:00:00')
  AND (NULL IS NULL OR m.movie_id = NULL)
  AND (FALSE = FALSE OR b.branch_id IN (0))
  AND (NULL IS NULL OR t.theater_type = NULL)
ORDER BY b.name, s.start_at;

-- ───────────────── 인덱스 추가 ─────────────────
-- 엔티티(Screening @Table)에 넣을 것과 같은 이름·순서다.
CREATE INDEX idx_screening_movie_start ON screening (movie_id, start_at);
ANALYZE TABLE screening;

-- ───────────────── 적용 후 ─────────────────
-- 위 1-A(EXPLAIN, EXPLAIN ANALYZE)와 1-B를 그대로 다시 실행한다.

-- ───────────────── 비용 ─────────────────
-- 인덱스별 크기(MB). PRIMARY가 테이블 본체다.
SELECT index_name, ROUND(stat_value * @@innodb_page_size / 1024 / 1024, 2) AS size_mb
FROM mysql.innodb_index_stats
WHERE database_name = DATABASE() AND table_name = 'screening' AND stat_name = 'size';
