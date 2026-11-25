// 시나리오 1: 조회 중심 트래픽 (로그인 불필요한 공개 API)
// 영화 목록 -> 영화관 -> 상영 시간표 -> 상영 정보 -> 좌석 조회 순서로, 사용자가 예매 화면까지 들어가는 흐름을 흉내낸다.
//
//   k6 run loadtest/browse.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, THINK_SCALE, fetchScreenings, randomItem, scaleVus } from './lib/common.js';

// 포화 지점 탐색: VU_SCALE=3 THINK_SCALE=0 k6 run loadtest/browse.js  (최대 600 VU, 요청 사이 대기 없음)
const vus = scaleVus;

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: vus(20) }, // 워밍업 (JIT, 커넥션 풀 준비)
        { duration: '1m', target: vus(50) },
        { duration: '1m', target: vus(100) },
        { duration: '1m', target: vus(200) },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export function setup() {
  return fetchScreenings();
}

function get(path, name) {
  const res = http.get(`${BASE_URL}${path}`, { tags: { name } });
  check(res, { [`${name} 200`]: (r) => r.status === 200 });
  return res;
}

export default function (data) {
  const screening = randomItem(data.screenings);
  const theaterId = randomItem(data.theaterIds);

  get('/api/movies', 'GET /api/movies');
  get('/api/theaters', 'GET /api/theaters');
  get(`/api/theaters/${theaterId}/screenings`, 'GET /api/theaters/{theaterId}/screenings');
  get(`/api/screenings/${screening.screeningId}`, 'GET /api/screenings/{screeningId}');
  get(`/api/screenings/${screening.screeningId}/seats`, 'GET /api/screenings/{screeningId}/seats');

  sleep((Math.random() * 0.7 + 0.3) * THINK_SCALE); // 사용자의 생각 시간 0.3~1.0초
}
