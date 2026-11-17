# CGV 조회 부하 테스트

영화 목록과 상영 좌석 조회를 각각 측정했다. 요청량을 올리면서 성공 처리량과 지연을 비교하고, 같은 시간대의 Grafana·Docker CPU·MySQL 쿼리 통계로 병목을 좁혔다.

## 실행하기

저장소 루트에서 별도 Compose 프로젝트로 실행한다. `.local-context/load-test/`를 만들고 `.env.example`을 바탕으로 `lab.env`에 `MYSQL_DATABASE=cgv_load_test`, `APP_PORT=18087`, `GRAFANA_PORT=13001`, `GRAFANA_ADMIN_PASSWORD`와 로컬 DB·JWT 값을 채운다.

```bash
lab() {
  docker compose -p cgv-week6-load-test --env-file .local-context/load-test/lab.env \
    -f compose.yml -f compose.monitoring.yml -f compose.load-test.yml "$@"
}

lab up -d --build --wait
lab exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch -u root "$MYSQL_DATABASE"' \
  < load-test/seed.sql

lab run --rm -T -e SCENARIO=movies -e RATE=25 -e DURATION=12s k6
lab run --rm -T -e SCENARIO=seats -e RATE=25 -e DURATION=12s k6

for scenario in movies seats; do
  for rate in 25 150 400 800; do
    lab run --rm -T -e SCENARIO="$scenario" -e RATE="$rate" -e DURATION=40s \
      -e PRE_ALLOCATED_VUS=140 -e MAX_VUS=300 k6
    sleep 3
  done
done
```

픽스처는 비어 있는 `cgv_load_test` DB에서 1회 실행한다. 영화 250편 중 200편을 공개하고, 상영 회차 `10001`에 좌석 200석과 확정 예매 40건을 만든다. k6 시작 전 두 API 각각의 응답 형태를 검증하며, 그 확인 요청은 결과 집계에서 제외한다.

각 단계는 초당 시작 요청 수를 고정하고 40초 동안 실행한다. 반복 1회당 GET 요청은 1건이다. 성공 RPS는 성공 응답 건수를 k6 실행 시간으로 나눈 값이다. `droppedIterations`는 VU가 부족해 시작하지 못한 반복 수이며 HTTP 실패율에는 포함되지 않는다. [k6 도착률 실행기](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/) · [k6 드롭 반복 설명](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/dropped-iterations/)

### 지표 채집

Grafana에서 테스트 시간 범위의 API 요청률, p95, JVM, Hikari 풀을 확인한다. Prometheus는 15초마다 수집하므로 Hikari 대기 수는 그 간격에 관측한 최대 표본이다. 단계별 성공 RPS와 p95/p99는 k6 요약으로 계산한다.

각 k6 실행 전후에 다음 값을 기록한다.

```bash
lab exec -T app cat /sys/fs/cgroup/cpu.stat
lab exec -T db cat /sys/fs/cgroup/cpu.stat
lab exec -T app curl -fsS http://localhost:8081/actuator/prometheus |
  grep -E '^hikaricp_connections_(acquire|usage)_seconds_(sum|count)'

lab exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch -u root "$MYSQL_DATABASE"' <<'SQL'
SELECT DIGEST, DIGEST_TEXT, COUNT_STAR, SUM_TIMER_WAIT, SUM_ROWS_EXAMINED, SUM_ROWS_SENT
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = DATABASE() AND DIGEST_TEXT LIKE 'SELECT %'
ORDER BY DIGEST;
SQL
```

- CPU는 `100 × usage_usec 증가분 ÷ 실행 시간(마이크로초)`로 계산한다. CPU 스로틀링은 `nr_throttled` 증가분을 `nr_periods` 증가분과 비교한다.
- Hikari 평균 연결 획득 시간은 `1,000 × acquire_seconds_sum 증가분 ÷ acquire_seconds_count 증가분`으로 계산한다. 연결 보유 시간에도 같은 방식으로 `usage` 값을 사용한다.
- MySQL은 API에 해당하는 SELECT의 `SUM_TIMER_WAIT` 증가분을 `COUNT_STAR` 증가분으로 나눠 평균 실행 시간을 구한다. `SUM_TIMER_WAIT`는 피코초 단위라 10억으로 나누면 밀리초가 된다. API 응답에는 JDBC 이동과 결과 처리 시간도 포함된다.

### 앱 CPU 한도 재검증

같은 앱 컨테이너를 유지한 채 `docker update --cpus 2 cgv-week6-load-test-app-1`을 실행하고 두 API를 800 req/s에서 한 번씩 다시 계측한다. `docker update --cpus 1 cgv-week6-load-test-app-1`로 복원한 뒤 같은 측정을 반복한다. DB는 모든 단계에서 1 vCPU다.

## 측정 결과

2026-11-17 KST, ARM64 Docker VM에서 k6 1.5.0, Java 21, MySQL 8.4.8을 사용했다. 앱과 DB는 각각 1 vCPU를 할당했고, Hikari 커넥션 풀의 상한은 5개였다. JSON 요청 로그와 모니터링 스택을 켠 상태에서 k6가 Compose 네트워크의 앱으로 요청을 보냈다.

| API | 목표 req/s | 성공 RPS | p95 | p99 | 미시작 반복 | 앱 CPU | DB CPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 영화 목록 | 25 | 25.0 | 8.7ms | 13.2ms | 0 | 16.6% | 3.2% |
| 영화 목록 | 150 | 150.0 | 6.8ms | 59.2ms | 0 | 37.8% | 8.5% |
| 영화 목록 | 400 | 399.0 | 29.0ms | 371.7ms | 33 | 36.8% | 14.1% |
| 영화 목록 | 800 | 733.5 | 698.6ms | 1,175.5ms | 2,654 | 94.2% | 31.1% |
| 좌석 조회 | 25 | 25.0 | 12.1ms | 24.7ms | 0 | 15.7% | 4.7% |
| 좌석 조회 | 150 | 149.9 | 6.2ms | 30.3ms | 0 | 31.1% | 11.7% |
| 좌석 조회 | 400 | 399.9 | 2.2ms | 21.1ms | 0 | 30.1% | 15.4% |
| 좌석 조회 | 800 | 781.2 | 492.6ms | 684.7ms | 711 | 90.7% | 40.9% |

시작한 HTTP 요청은 전 구간에서 모두 `200`이었다. 800 req/s 단계에서 목표량 대비 미시작 반복은 영화 목록 8.3%, 좌석 조회 2.2%였다. CPU의 100%는 1 vCPU 사용량을 나타낸다.

### 병목 분석

800 req/s 단계에서 앱이 CPU 한도에 걸리는 동안 Hikari 연결 5개가 모두 사용 중인 표본이 나타났다. Grafana의 대기 수는 영화 목록에서 최대 192개, 좌석 조회에서 최대 195개였다. 같은 구간의 DB CPU는 각각 31.1%, 40.9%였고, 두 구간 모두 DB CPU 스로틀링은 0주기였다. k6는 VU 부족으로 일부 반복을 시작하지 못했고, 이 반복은 응답 시간과 HTTP 오류율 집계에서 빠진다. [영화 목록 800 req/s Grafana](http://127.0.0.1:13001/d/cgv-overview/cgv-overview?from=1794877769415&to=1794877809855) · [좌석 조회 800 req/s Grafana](http://127.0.0.1:13001/d/cgv-overview/cgv-overview?from=1794877946383&to=1794877987016)

**앱 CPU 한도를 1 → 2 → 1 vCPU로 바꾸자 대기와 지연이 함께 낮아졌다가 다시 올라갔다.** 앱 프로세스는 재시작하지 않았고, DB·데이터·목표 요청 수를 동일하게 유지했다.

| API | 앱 CPU 한도 | 성공 RPS | p95 | 평균 연결 획득 시간 | 앱 CPU 제한 주기 | 미시작 반복 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 영화 목록 | 1 vCPU 초기 | 733.5 | 698.6ms | 203.19ms | 363/406 | 2,654 |
| 영화 목록 | 2 vCPU | 799.7 | 2.6ms | 0.26ms | 3/404 | 0 |
| 영화 목록 | 1 vCPU 복원 | 797.0 | 234.3ms | 30.49ms | 108/405 | 114 |
| 좌석 조회 | 1 vCPU 초기 | 781.2 | 492.6ms | 142.33ms | 326/406 | 711 |
| 좌석 조회 | 2 vCPU | 795.8 | 38.6ms | 5.50ms | 29/405 | 153 |
| 좌석 조회 | 1 vCPU 복원 | 768.4 | 397.8ms | 64.42ms | 130/411 | 968 |

이 실험에서 지연을 키운 주요 요인은 **앱의 CPU 한도와 그때 커진 연결 획득 대기**였다. 영화 목록 SQL은 공개 영화 200편을 읽어 평균 0.24ms에 실행됐다. 좌석 조회는 상영·영화·상영관 조회와 좌석 및 예매 상태 조회로 평균 약 네 개의 SELECT를 실행했고, 각 평균을 더하면 약 0.32ms였다. 이 값은 MySQL 내부 평균으로, 네트워크와 앱의 조회 결과 처리 시간은 별도로 든다. CPU 사용량을 엔티티 매핑, JSON 변환, 로깅 등으로 나누려면 앱 프로파일링이 필요하다.

`EXPLAIN`에서는 영화 250편과 좌석 200석을 각각 스캔하는 계획이 나왔다. 영화의 80%가 공개 상태이고 좌석은 한 상영 회차에만 들어 있는 픽스처다. 인덱스 효과를 비교하려면 영화와 회차 간 데이터 분포를 늘린 실험이 필요하다. 현재 수치는 일부 데이터를 반복 조회한 로컬 환경의 결과로, 단계마다 변동이 있어 지속 가능한 상한을 구하려면 구간을 더 잘게 나눠 반복 측정해야 한다.

### 다음 최적화 실험 후보

- 영화 목록 응답은 200편 기준 40,829바이트였다. 목록에 필요한 필드나 한 번에 보내는 편수를 줄인 뒤, 동일한 부하로 앱 CPU와 p95를 비교할 수 있다. 반복되는 공개 목록을 캐시한다면 영화 등록과 비공개 처리 시 갱신 기준을 같이 정한다.
- 좌석 조회 응답은 10,538바이트였다. 상영·영화·상영관의 조회를 한 번으로 줄이거나 좌석 상태에 필요한 컬럼만 조회하는 변경을 비교할 수 있다. 좌석 상태는 예약과 선점 만료에 따라 바뀌므로 화면에 표시되는 최신 상태와 캐시 무효화 기준을 먼저 정한다.
