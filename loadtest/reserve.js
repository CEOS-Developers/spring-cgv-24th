// 시나리오 2: 예매 쓰기 트래픽 (로그인한 사용자가 좌석 조회 후 예매, 이어서 취소)
// 여러 상영 일정에 흩어져 예매하므로 "일반적인 쓰기 부하"에 해당한다. (같은 좌석 경합은 contention.js)
//
// 한 사용자가 동시에 보유할 수 있는 결제 대기(HOLD) 예매는 3건이라, 예매 직후 바로 취소해서 상한에 걸리지 않게 한다.
// 사용자는 100명이므로 VU가 100을 넘으면 사용자 1명을 VU 여러 개가 나눠 쓴다.
//
//   k6 run loadtest/reserve.js
//   포화 지점 탐색: VU_SCALE=4 THINK_SCALE=0 k6 run loadtest/reserve.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  BASE_URL,
  THINK_SCALE,
  USER_COUNT,
  authParams,
  fetchScreenings,
  loginUsers,
  randomItem,
  scaleVus,
} from './lib/common.js';

const reservationCreated = new Counter('reservation_created');
const reservationConflict = new Counter('reservation_conflict');
const reservationUnexpected = new Counter('reservation_unexpected');

export const options = {
  scenarios: {
    reserve: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: scaleVus(10) }, // 워밍업
        { duration: '1m', target: scaleVus(30) },
        { duration: '1m', target: scaleVus(50) },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '15s',
    },
  },
  thresholds: {
    reservation_unexpected: ['count<1'], // 201/409 이외의 응답이 나오면 실패로 본다
    'http_req_duration{name:POST /api/screenings/{screeningId}/reservations}': ['p(95)<1000'],
  },
};

export function setup() {
  const { screenings } = fetchScreenings();
  // 로그인 해시 연산이 무거우므로 시작 전에 한 번만 로그인해 토큰을 재사용한다 (access token 유효 1시간)
  const tokens = loginUsers(USER_COUNT);
  return { screenings, tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const screening = randomItem(data.screenings);

  // 1. 좌석 조회 (예매 가능한 좌석만 골라서 요청하는 정상적인 클라이언트 동작)
  const seatsRes = http.get(
    `${BASE_URL}/api/screenings/${screening.screeningId}/seats`,
    authParams(token, 'GET /api/screenings/{screeningId}/seats'),
  );
  if (!check(seatsRes, { '좌석 조회 200': (r) => r.status === 200 })) {
    return;
  }
  const available = seatsRes.json('data.seats').filter((s) => s.isAvailable);
  if (available.length < 2) {
    return;
  }
  const picked = [randomItem(available).seatNumber, randomItem(available).seatNumber];

  // 2. 예매 (다른 사용자와 겹치면 409가 정상 응답이다)
  const params = authParams(token, 'POST /api/screenings/{screeningId}/reservations');
  params.responseCallback = http.expectedStatuses(201, 409);
  const res = http.post(
    `${BASE_URL}/api/screenings/${screening.screeningId}/reservations`,
    JSON.stringify({ seatNumbers: picked }),
    params,
  );

  if (res.status === 201) {
    reservationCreated.add(1);
    // 3. 결제 없이 바로 취소해서 좌석과 HOLD 한도를 되돌린다
    const reservationId = res.json('data.reservationId');
    sleep(0.2);
    http.del(`${BASE_URL}/api/reservations/${reservationId}`, null, authParams(token, 'DELETE /api/reservations/{reservationId}'));
  } else if (res.status === 409) {
    reservationConflict.add(1);
  } else {
    reservationUnexpected.add(1);
    console.error(`예상하지 못한 응답: ${res.status} ${res.body}`);
  }

  sleep((Math.random() * 0.7 + 0.3) * THINK_SCALE);
}
