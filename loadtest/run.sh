#!/usr/bin/env bash
# 부하테스트 실행 스크립트
#
#   ADMIN_TOKEN=<서버의 ADMIN_TOKEN> ./loadtest/run.sh seed        # 데이터 준비 (처음 1번)
#   ./loadtest/run.sh browse                                       # 조회 시나리오
#   ./loadtest/run.sh reserve                                      # 예매 시나리오
#   ./loadtest/run.sh contention                                   # 좌석 경합 시나리오
#   ./loadtest/run.sh all                                          # 3개를 순서대로 (시나리오 사이 30초 휴식)
#
# 각 시나리오의 시작/종료 시각을 출력한다. Grafana에서 같은 구간을 선택해 지표를 대조하기 위한 용도이다.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p loadtest/results
TS=$(date +%Y%m%d-%H%M%S)

run() {
  local name=$1
  echo "=== ${name} 시작: $(date '+%F %T')"
  k6 run --summary-export="loadtest/results/${name}-${TS}.json" "loadtest/${name}.js"
  echo "=== ${name} 종료: $(date '+%F %T')"
}

case "${1:-all}" in
  seed) k6 run loadtest/seed.js ;;
  browse | reserve | contention) run "$1" ;;
  all)
    run browse
    sleep 30
    run reserve
    sleep 30
    run contention
    ;;
  *)
    echo "사용법: $0 {seed|browse|reserve|contention|all}" >&2
    exit 1
    ;;
esac
