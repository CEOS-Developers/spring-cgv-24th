// 캐시 대상 — 영화 목록(예매율 순). 요청마다 예매 좌석 전체를 집계하는 API라 캐시 전후 차이를 본다.
// docs/perf/seed.sql(예매 좌석 약 104만 행)을 넣은 뒤 실행한다. 시드 없이는 집계가 가벼워 차이가 드러나지 않는다.
// 캐시 전: CACHE_TYPE=none으로 앱을 띄운다. 같은 단계로 두 번 돌려 비교한다.
import http from 'k6/http';
import { BASE_URL, COMMON_THRESHOLDS, recordErrors } from './lib/common.js';

export const options = {
  scenarios: {
    movies: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 20,
      maxVUs: 300,
      stages: [
        { target: 1, duration: '1m' },
        { target: 10, duration: '10s' }, { target: 10, duration: '1m' },
        { target: 50, duration: '10s' }, { target: 50, duration: '1m' },
      ],
    },
  },
  // 캐시가 없으면 집계 한 번에 약 8초라 초당 1건도 버티지 못한다. 무너진 뒤 요청을 계속 쌓으면 중단해도 MySQL이
  // 한참 바쁘게 남아 다음 측정을 오염시키므로, 5xx가 나오면 10초 뒤 멈춘다.
  thresholds: {
    server_errors: [{ threshold: 'rate==0', abortOnFail: true, delayAbortEval: '10s' }],
    no_response: COMMON_THRESHOLDS.no_response,
    'http_req_duration{name:GET /api/movies}': ['p(95)>=0'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/movies`, { tags: { name: 'GET /api/movies' } });
  recordErrors(res);
}
