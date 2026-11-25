-- 기존 MySQL DB에 적용할 영화 목록 정렬 인덱스.
-- 적용 전 SHOW INDEX FROM movies로 같은 인덱스가 없는지 확인한다.
CREATE INDEX idx_movies_open_date_id
    ON movies (open_date DESC, movie_id DESC);
