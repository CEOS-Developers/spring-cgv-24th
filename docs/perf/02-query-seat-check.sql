-- ② 쿼리: 좌석 선점의 점유 확인. 220석이 찬 회차 900001에 2석(11행 1·2열)을 선점하려는 상황.
-- 각각 두 번 실행해 두 번째 결과를 쓴다.

-- 적용 전: 회차의 점유 좌석을 전부 읽는다 (findOccupiedPositionsByScreeningId)
EXPLAIN ANALYZE
SELECT rs.row_num, rs.col_num
FROM reservation_seat rs
JOIN reservation r ON r.reservation_id = rs.reservation_id
WHERE rs.screening_id = 900001
  AND rs.release_key = 0
  AND (r.status <> 'PENDING' OR r.expires_at > NOW())
ORDER BY rs.row_num, rs.col_num;

-- 적용 후: 요청한 좌석만 읽는다 (findOccupiedPositionsAmong)
EXPLAIN ANALYZE
SELECT rs.row_num, rs.col_num
FROM reservation_seat rs
JOIN reservation r ON r.reservation_id = rs.reservation_id
WHERE rs.screening_id = 900001
  AND rs.release_key = 0
  AND rs.row_num * 100 + rs.col_num IN (1101, 1102)
  AND (r.status <> 'PENDING' OR r.expires_at > NOW());
