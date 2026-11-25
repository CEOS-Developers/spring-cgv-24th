# 7주차 로컬 실험 기록

## 환경

- MySQL 8.4, 별도 Compose 프로젝트 `cgv-week7`
- `seed-movies.sql`로 빈 `movies` 테이블에 20,000건 생성
- 외부 공개 포트와 겹치지 않도록 앱을 `127.0.0.1:18080`에 연결
- 단일 개발 PC에서 측정한 참고값. 반복 실행과 신뢰 구간을 갖춘 부하 시험 결과는 아님

## 인덱스 전후

동일 SQL:

```sql
EXPLAIN ANALYZE
SELECT movie_id, movie_title, running_time, open_date, status
FROM movies
ORDER BY open_date DESC, movie_id DESC
LIMIT 50;
```

인덱스 적용 전:

```text
Limit: 50 row(s) (actual time=5.41..5.42 rows=50)
  Sort: open_date DESC, movie_id DESC (actual time=5.41 rows=50)
    Table scan on movies (actual time=0.0502..3.4 rows=20000)
```

`add-movie-list-index.sql` 적용 후:

```text
Limit: 50 row(s) (actual time=0.129..0.182 rows=50)
  Index scan on movies using idx_movies_open_date_id
    (actual time=0.0488..0.0992 rows=50)
```

이 결과는 한 번씩 실행한 계획이다. 현재 API에서는 `GET /api/movies?page=0&size=50`으로 같은 상위 50건을 조회한다.

## 쿼리와 캐시

`curl -o NUL -w "%{http_code} %{time_total}"`로 HTTP 시간을 측정했다. 캐시가 있는 최종 코드에서 첫 전체 목록 요청을 미적중으로, 이후 요청을 적중으로 기록했다.

| 상태 | 시간 |
| --- | ---: |
| 기존 엔티티 전체 조회와 DTO 변환, 인덱스 적용 후 | 2.683초 |
| DTO 프로젝션, 캐시 미적중 | 0.833초 |
| DTO 프로젝션, 캐시 적중 1 | 0.110초 |
| DTO 프로젝션, 캐시 적중 2 | 0.083초 |
| DTO 프로젝션, 캐시 적중 3 | 0.084초 |
| DTO 프로젝션, 캐시 적중 4 | 0.095초 |
| DTO 프로젝션, 캐시 적중 5 | 0.098초 |
| 페이지 50건 조회 | 0.038초 |

전체 목록은 20,000건을 직렬화하므로 캐시 적중에도 JSON 생성·전송 시간이 남는다. 엔티티 조회와 DTO 프로젝션의 차이는 단일 HTTP 측정이므로 정확한 개선 비율로 일반화하지 않는다.

## 검증

- `MovieListCacheTest`: 두 번 읽을 때 저장소 조회 1회, 생성·삭제 후 각각 재조회 확인
- `GET /api/movies?page=0&size=50`: HTTP 200, 결과 50건 확인
- 전체 `./gradlew test --no-daemon`: 실험용 MySQL과 같은 Docker 네트워크에서 27건 통과
