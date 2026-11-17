// 병목 후보 2 — 좌석 배치도 조회 폭주.
// 예매가 열리면 같은 회차 배치도를 계속 새로 고친다. 캐시가 없어 요청마다 점유 좌석 조인 쿼리가 나간다.
// 반쯤 찬 회차(132석 점유)를 써서 조회가 실제 크기의 결과를 다루게 한다.
import { COMMON_THRESHOLDS, HALF_FULL_SCREENING, viewSeatMap } from './lib/common.js';

export const options = {
  scenarios: {
    seatMap: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1000,
      stages: [
        { target: 50, duration: '1m' },
        { target: 200, duration: '10s' }, { target: 200, duration: '1m' },
        { target: 500, duration: '10s' }, { target: 500, duration: '1m' },
        { target: 1000, duration: '10s' }, { target: 1000, duration: '1m' },
      ],
    },
  },
  thresholds: {
    ...COMMON_THRESHOLDS,
    'http_req_duration{name:GET /api/screenings/{id}/seats}': ['p(95)>=0'],
  },
};

export default function () {
  viewSeatMap(HALF_FULL_SCREENING);
}
