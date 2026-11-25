// 병목 후보 1 — 좌석 선점 쓰기 경합.
// 같은 회차·인기 좌석에 선점이 몰리면 유니크 인덱스에서 락을 기다리는 요청이 커넥션을 쥔 채 최대 3초 머문다.
// 이게 풀(20개)을 말려 무관한 API까지 끌어내리는지를 probe(GET /api/movies)로 함께 본다.
import http from 'k6/http';
import { COMMON_THRESHOLDS, SEAT_RESULT_THRESHOLDS, BASE_URL, loginMany, pickScreening, holdWithRetry, recordErrors } from './lib/common.js';

const TOKEN_COUNT = 200;

export const options = {
  setupTimeout: '180s',
  scenarios: {
    holds: {
      executor: 'ramping-arrival-rate',
      exec: 'hold',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 600,
      stages: [
        { target: 20, duration: '1m' },
        { target: 50, duration: '10s' }, { target: 50, duration: '1m' },
        { target: 100, duration: '10s' }, { target: 100, duration: '1m' },
        { target: 200, duration: '10s' }, { target: 200, duration: '1m' },
      ],
    },
    probe: {
      executor: 'constant-arrival-rate',
      exec: 'probe',
      rate: 5,
      timeUnit: '1s',
      duration: '4m30s',
      preAllocatedVUs: 5,
      maxVUs: 50,
    },
  },
  thresholds: {
    ...COMMON_THRESHOLDS,
    ...SEAT_RESULT_THRESHOLDS,
    'http_req_duration{name:probe GET /api/movies}': ['p(95)>=0'],
    'http_req_duration{name:POST /api/reservations}': ['p(95)>=0'],
  },
};

export function setup() {
  return { tokens: loginMany(TOKEN_COUNT) };
}

export function hold(data) {
  const token = data.tokens[Math.floor(Math.random() * data.tokens.length)];
  holdWithRetry(token, pickScreening(), 2);
}

export function probe() {
  const res = http.get(`${BASE_URL}/api/movies`, { tags: { name: 'probe GET /api/movies' } });
  recordErrors(res);
}
