// 부하를 걸기 전에 환경이 맞는지 한 번씩만 확인한다. 시드·프로필·토큰이 어긋난 채로 부하를 걸면
// 실패가 "앱이 못 버틴다"로 보인다.
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1'] },
};

function login(loginId) {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ loginId, password: 'loadtest1!' }), { headers: JSON_HEADERS });
  check(res, { [`${loginId} 로그인 200`]: (r) => r.status === 200 });
  return res.json('data.accessToken');
}

export default function () {
  const token = login('loadtest0001');
  login('loadtest1000');

  const seats = http.get(`${BASE_URL}/api/screenings/2001/seats`);
  check(seats, { '배치도 조회 200': (r) => r.status === 200 });

  const hold = http.post(`${BASE_URL}/api/reservations`, JSON.stringify({
    screeningId: 1001,
    seats: [
      { rowNum: 12, colNum: 1, audienceType: 'ADULT' },
      { rowNum: 12, colNum: 2, audienceType: 'ADULT' },
    ],
  }), { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } });
  check(hold, { '좌석 선점 201': (r) => r.status === 201 });

  check(http.get(`${BASE_URL}/api/movies`), { '영화 목록 200': (r) => r.status === 200 });
}
