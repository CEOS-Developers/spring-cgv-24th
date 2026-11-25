#!/usr/bin/env bash
# 사용법: MYSQL_PWD=... ./expired-hold-index-measure.sh <label> [반복 횟수]
# findExpiredHoldIds()가 실행하는 SQL을 고정 시각으로 EXPLAIN / EXPLAIN ANALYZE 하고, 반복 측정의 중앙값을 출력한다
set -euo pipefail
MYSQL="${MYSQL:-mysql}"
LABEL="$1"; RUNS="${2:-21}"
Q="SELECT r1_0.id FROM reservation r1_0 WHERE r1_0.status='PENDING_PAYMENT' AND r1_0.expires_at<='2026-11-25 12:00:00'"
run() { "$MYSQL" -uroot -h127.0.0.1 cgv_index_lab "$@"; }

echo "===== [$LABEL] SHOW INDEX"
run -e "SHOW INDEX FROM reservation" | cut -f3,4,5
echo "===== [$LABEL] EXPLAIN"
run -e "EXPLAIN $Q\G"
echo "===== [$LABEL] 읽은 행 수 (Handler_read_*)"
run -e "FLUSH STATUS; $Q; SHOW SESSION STATUS LIKE 'Handler_read%';" | grep Handler
echo "===== [$LABEL] 결과 건수"
run -N -e "SELECT COUNT(*) FROM ($Q) t"
run -N -e "$Q" > /dev/null  # 워밍업
echo "===== [$LABEL] EXPLAIN ANALYZE (대표 1회)"
run -N -r -e "EXPLAIN ANALYZE $Q"
echo "===== [$LABEL] EXPLAIN ANALYZE ${RUNS}회 최상위 actual time(ms)"
for i in $(seq "$RUNS"); do
  run -N -r -e "EXPLAIN ANALYZE $Q" | head -1 | sed -E 's/.*actual time=[0-9.e+]+\.\.([0-9.e+]+).*/\1/'
done | sort -g | awk '{a[NR]=$1} END {printf "min=%s median=%s max=%s (n=%d)\n", a[1], a[int((NR+1)/2)], a[NR], NR}'
