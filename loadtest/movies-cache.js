// 시나리오: 영화 목록 캐시 효과 (캐시 적용 전후를 같은 조건으로 비교)
//
//   k6 run loadtest/movies-cache.js
//   VUS=100 DURATION=30s k6 run loadtest/movies-cache.js
//
// sleep 없이 계속 요청해서 서버가 처리할 수 있는 최대치를 본다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/common.js';

const VUS = parseInt(__ENV.VUS || '50');
const DURATION = __ENV.DURATION || '30s';

export const options = {
    scenarios: {
        movies: { executor: 'constant-vus', vus: VUS, duration: DURATION },
    },
    thresholds: {
        'http_req_duration{name:GET /api/movies}': [],
    },
};

export default function () {
    const res = http.get(`${BASE_URL}/api/movies`, { tags: { name: 'GET /api/movies' } });
    check(res, { '영화 조회 200': (r) => r.status === 200 });
}
