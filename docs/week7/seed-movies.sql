-- 7주차 로컬 성능 실험 전용. 새로 만든 빈 DB에서만 실행한다.
INSERT INTO movies
    (created_at, updated_at, description, end_date, open_date,
     running_time, status, movie_title)
WITH digits AS (
    SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
    UNION ALL SELECT 8 UNION ALL SELECT 9
)
SELECT NOW(6), NOW(6), REPEAT('movie detail ', 40), NULL,
       DATE_ADD('2020-01-01', INTERVAL MOD(n, 1000) DAY),
       90 + MOD(n, 60), 'SHOWING', CONCAT('Week7 Movie ', LPAD(n, 5, '0'))
FROM (
    SELECT a.d + 10*b.d + 100*c.d + 1000*d.d + 10000*e.d AS n
    FROM digits a
    CROSS JOIN digits b
    CROSS JOIN digits c
    CROSS JOIN digits d
    CROSS JOIN digits e
) numbers
WHERE n < 20000;
