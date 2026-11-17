// 시나리오 3: 같은 좌석에 몰리는 경합 상황 (인기 상영 오픈 직후를 가정)
// 모든 사용자가 한 상영 일정의 좌석 1~3번만 노린다. 이론상 좌석당 1명만 성공하고 나머지는 409(이미 예매됨)여야 한다.
// 확인하려는 것: ① 중복 예매가 생기지 않는가 ② 경합 중 응답시간과 5xx 에러가 어떻게 변하는가
//
//   k6 run loadtest/contention.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE_URL, THINK_SCALE, USER_COUNT, authParams, fetchScreenings, loginUsers, scaleVus } from './lib/common.js';

const reservationCreated = new Counter('reservation_created');
const reservationConflict = new Counter('reservation_conflict');
const reservationUnexpected = new Counter('reservation_unexpected');

const HOT_SEATS = [1, 2, 3];

export const options = {
  scenarios: {
    contention: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: scaleVus(20) },
        { duration: '1m', target: scaleVus(50) },
        { duration: '20s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    reservation_unexpected: ['count<1'],
  },
};

export function setup() {
  const { screenings } = fetchScreenings();
  const tokens = loginUsers(USER_COUNT);
  return { hotScreeningId: screenings[0].screeningId, tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const seat = HOT_SEATS[Math.floor(Math.random() * HOT_SEATS.length)];

  const params = authParams(token, 'POST /api/screenings/{screeningId}/reservations');
  params.responseCallback = http.expectedStatuses(201, 409);
  const res = http.post(
    `${BASE_URL}/api/screenings/${data.hotScreeningId}/reservations`,
    JSON.stringify({ seatNumbers: [seat] }),
    params,
  );

  if (res.status === 201) {
    reservationCreated.add(1);
    // 잠깐 점유했다가 취소한다 (결제 화면에서 머무는 시간을 흉내)
    sleep(0.3);
    const reservationId = res.json('data.reservationId');
    check(
      http.del(`${BASE_URL}/api/reservations/${reservationId}`, null, authParams(token, 'DELETE /api/reservations/{reservationId}')),
      { '취소 200': (r) => r.status === 200 },
    );
  } else if (res.status === 409) {
    reservationConflict.add(1);
  } else {
    reservationUnexpected.add(1);
    console.error(`예상하지 못한 응답: ${res.status} ${res.body}`);
  }

  sleep(0.1 * THINK_SCALE);
}
