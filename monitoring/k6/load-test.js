import http from 'k6/http';
import { check, sleep } from 'k6';

// 대시보드에 의미 있는 데이터를 채우기 위한 부하다. t3.micro 한 대가 감당할 수준에서 실제 사용 흐름과 비정상 요청을 섞는다
const BASE_URL = __ENV.BASE_URL;
const USER_COUNT = Number(__ENV.USER_COUNT || 10);
const PASSWORD = __ENV.LOADTEST_PASSWORD || 'loadtest-password';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      stages: [
        { duration: '1m', target: 10 },
        { duration: '3m', target: 20 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<500'],
    checks: ['rate>0.99'],
  },
};

function data(res) {
  return res.json('data');
}

function pick(items) {
  return items[Math.floor(Math.random() * items.length)];
}

export function setup() {
  if (!BASE_URL) {
    throw new Error('BASE_URL을 지정하세요. 예: -e BASE_URL=https://api.example.com');
  }
  const tokens = [];
  for (let i = 1; i <= USER_COUNT; i++) {
    const loginId = `loadtest${String(i).padStart(2, '0')}`;
    http.post(`${BASE_URL}/api/auth/signup`, JSON.stringify({ loginId, nickname: loginId, password: PASSWORD }),
      { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(201, 409) });
    const login = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ loginId, password: PASSWORD }),
      { headers: JSON_HEADERS });
    if (login.status !== 200) {
      throw new Error(`부하 테스트 계정 로그인 실패 loginId=${loginId} status=${login.status}`);
    }
    tokens.push(data(login).accessToken);
  }
  const movieIds = data(http.get(`${BASE_URL}/api/movies`)).map((movie) => movie.id);
  const theaterIds = data(http.get(`${BASE_URL}/api/theaters`)).map((theater) => theater.id);
  return { tokens, movieIds, theaterIds };
}

export default function ({ tokens, movieIds, theaterIds }) {
  const ok = (res) => check(res, { 'status 2xx': (r) => r.status >= 200 && r.status < 300 });

  ok(http.get(`${BASE_URL}/api/movies`, { tags: { name: 'GET /api/movies' } }));
  if (movieIds.length > 0) {
    ok(http.get(`${BASE_URL}/api/movies/${pick(movieIds)}`, { tags: { name: 'GET /api/movies/{id}' } }));
  }
  ok(http.get(`${BASE_URL}/api/theaters`, { tags: { name: 'GET /api/theaters' } }));
  ok(http.get(`${BASE_URL}/api/items`, { tags: { name: 'GET /api/items' } }));

  const auth = { headers: { ...JSON_HEADERS, Authorization: `Bearer ${pick(tokens)}` } };
  ok(http.get(`${BASE_URL}/api/reservations`, { ...auth, tags: { name: 'GET /api/reservations' } }));
  ok(http.get(`${BASE_URL}/api/orders`, { ...auth, tags: { name: 'GET /api/orders' } }));
  if (theaterIds.length > 0) {
    const theaterId = pick(theaterIds);
    ok(http.get(`${BASE_URL}/api/theaters/${theaterId}/stocks`, { tags: { name: 'GET /api/theaters/{id}/stocks' } }));
    const like = http.post(`${BASE_URL}/api/theaters/${theaterId}/likes`, null,
      { ...auth, tags: { name: 'POST /api/theaters/{id}/likes' }, responseCallback: http.expectedStatuses(201, 409) });
    if (like.status === 201) {
      ok(http.del(`${BASE_URL}/api/theaters/${theaterId}/likes`, null, { ...auth, tags: { name: 'DELETE /api/theaters/{id}/likes' } }));
    }
  }

  // 실제 서비스에서도 늘 섞여 들어오는 4xx: 잘못된 비밀번호, 토큰 없는 요청, 스캐너의 존재하지 않는 경로
  if (Math.random() < 0.1) {
    http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ loginId: 'loadtest01', password: 'wrong-password' }),
      { headers: JSON_HEADERS, tags: { name: 'POST /api/auth/login (wrong)' }, responseCallback: http.expectedStatuses(401) });
  }
  if (Math.random() < 0.05) {
    http.get(`${BASE_URL}/api/reservations`, { tags: { name: 'GET /api/reservations (no token)' }, responseCallback: http.expectedStatuses(401) });
  }
  if (Math.random() < 0.05) {
    http.get(`${BASE_URL}/wp-admin/setup.php`, { tags: { name: 'GET /wp-admin (scanner)' }, responseCallback: http.expectedStatuses(404) });
  }

  sleep(1 + Math.random());
}
