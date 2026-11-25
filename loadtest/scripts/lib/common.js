// 시나리오 공통. 시드 번호는 src/main/resources/db/loadtest-data.sql과 맞춘다.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const PASSWORD = 'loadtest1!';
export const USER_COUNT = 1000;
export const FIRST_SCREENING = 1001;
export const SCREENING_COUNT = 100;
export const HALF_FULL_SCREENING = 2001;
const ROWS = 12;
const COLS = 22;

// 좌석 경쟁에서 진 409는 정상 응답이다. 실패율(http_req_failed)에 넣으면 실패가 아닌 것이 실패로 보인다.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 409));

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const serverErrors = new Rate('server_errors');
export const seatHoldResult = new Counter('seat_hold_result');

// 판정 기준은 5xx 0건. 409·401은 업무상 정상일 수 있어 http_req_failed로는 판정하지 않는다.
export const COMMON_THRESHOLDS = {
  server_errors: ['rate==0'],
};

export function loginId(n) {
  return `loadtest${String(n).padStart(4, '0')}`;
}

export function login(id, tags = { name: 'POST /api/auth/login' }) {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ loginId: id, password: PASSWORD }), { headers: JSON_HEADERS, tags });
  serverErrors.add(res.status >= 500);
  return res.status === 200 ? res.json('data.accessToken') : null;
}

// 측정 전에 토큰을 받아 둔다. phase:setup 태그로 대시보드에서 측정 구간과 가른다.
export function loginMany(count) {
  const tokens = [];
  for (let start = 1; start <= count; start += 50) {
    const end = Math.min(start + 49, count);
    const requests = [];
    for (let n = start; n <= end; n++) {
      requests.push(['POST', `${BASE_URL}/api/auth/login`,
        JSON.stringify({ loginId: loginId(n), password: PASSWORD }),
        { headers: JSON_HEADERS, tags: { name: 'setup login', phase: 'setup' } }]);
    }
    http.batch(requests).forEach((r) => {
      if (r.status !== 200) throw new Error(`setup 로그인 실패 status=${r.status}`);
      tokens.push(r.json('data.accessToken'));
    });
  }
  return tokens;
}

export function auth(token) {
  return { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
}

function gaussian(mean, sd) {
  const u = 1 - Math.random();
  const v = Math.random();
  return mean + sd * Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
}

function clamp(x, min, max) {
  return Math.max(min, Math.min(max, Math.round(x)));
}

// 실제 사용자는 가운데·약간 뒤쪽을 먼저 노린다. 균등하게 고르면 경합이 실제보다 약하게 나온다.
export function pickSeats(count) {
  const row = clamp(gaussian(8, 2.5), 1, ROWS);
  const startCol = clamp(gaussian((COLS - count) / 2 + 1, 4), 1, COLS - count + 1);
  const seats = [];
  for (let i = 0; i < count; i++) {
    seats.push({ rowNum: row, colNum: startCol + i, audienceType: 'ADULT' });
  }
  return seats;
}

// 10초마다 새 회차가 "예매 오픈"된다. 고정된 인기 회차만 쓰면 1분 안에 매진돼
// 나머지 구간은 이미 팔린 좌석을 거절하는 가벼운 경로만 재게 된다.
export function hotScreening() {
  const elapsed = Date.now() - exec.scenario.startTime;
  return FIRST_SCREENING + (Math.floor(elapsed / 10000) % SCREENING_COUNT);
}

// 80%는 지금 열린 인기 회차, 20%는 나머지 회차에 흩어진다.
export function pickScreening() {
  if (Math.random() < 0.8) return hotScreening();
  return FIRST_SCREENING + Math.floor(Math.random() * SCREENING_COUNT);
}

export function viewSeatMap(screeningId) {
  const res = http.get(`${BASE_URL}/api/screenings/${screeningId}/seats`,
    { tags: { name: 'GET /api/screenings/{id}/seats' } });
  serverErrors.add(res.status >= 500);
  return res;
}

// 결과를 created / already_reserved(경쟁에서 짐) / conflict(락 대기 초과) / other로 나눠 센다.
export function holdSeats(token, screeningId, seats) {
  const res = http.post(`${BASE_URL}/api/reservations`,
    JSON.stringify({ screeningId, seats }),
    { headers: auth(token), tags: { name: 'POST /api/reservations' } });
  serverErrors.add(res.status >= 500);
  let result = 'other';
  if (res.status === 201) {
    result = 'created';
  } else if (res.status === 409) {
    const code = res.json('code');
    if (code === 'SEAT_ALREADY_RESERVED') result = 'already_reserved';
    else if (code === 'SEAT_RESERVATION_CONFLICT') result = 'conflict';
  }
  seatHoldResult.add(1, { result });
  return result;
}

// 409(이미 선택됨)면 사용자는 다른 좌석을 골라 다시 누른다. 최대 2번 더.
export function holdWithRetry(token, screeningId, seatCount) {
  let result = holdSeats(token, screeningId, pickSeats(seatCount));
  for (let i = 0; i < 2 && result === 'already_reserved'; i++) {
    result = holdSeats(token, screeningId, pickSeats(seatCount));
  }
  return result;
}

// end-of-test 요약에 결과별 건수가 찍히도록 태그별 하위 지표에 항상 참인 임계값을 건다.
export const SEAT_RESULT_THRESHOLDS = {
  'seat_hold_result{result:created}': ['count>=0'],
  'seat_hold_result{result:already_reserved}': ['count>=0'],
  'seat_hold_result{result:conflict}': ['count>=0'],
  'seat_hold_result{result:other}': ['count>=0'],
};
