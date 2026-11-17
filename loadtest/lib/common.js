import http from 'k6/http';
import { check, fail } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 부하테스트 전용 더미 계정. 로컬 테스트 DB에서만 사용한다.
export const USER_PREFIX = 'lt_user_';
export const USER_PASSWORD = __ENV.USER_PASSWORD || 'LoadTest123!';
export const USER_COUNT = parseInt(__ENV.USERS || '100');
export const ADMIN_LOGIN_ID = 'lt_admin';

export const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 사용자의 "생각 시간"(요청 사이 대기)을 줄이거나 없앤다. 서버 포화 지점을 찾을 때 THINK_SCALE=0 으로 쓴다.
export const THINK_SCALE = parseFloat(__ENV.THINK_SCALE || '1');
// VU 수 배율. 예) VU_SCALE=3 이면 최대 VU가 3배
export const VU_SCALE = parseFloat(__ENV.VU_SCALE || '1');
export const scaleVus = (n) => Math.max(1, Math.round(n * VU_SCALE));

export function userLoginId(index) {
  return `${USER_PREFIX}${index}`;
}

export function authParams(token, name) {
  return {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    // 경로 변수(/api/screenings/1, /2 ...)마다 지표가 따로 쌓이지 않도록 name 태그로 묶는다
    tags: { name },
  };
}

export function login(loginId, password) {
  const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ loginId, password }), {
    headers: JSON_HEADERS,
    tags: { name: 'POST /api/auth/login' },
  });
  if (res.status !== 200) {
    return null;
  }
  return res.json('data.accessToken');
}

// 사용자 N명을 한 번에 로그인시켜 토큰 배열을 만든다. (비밀번호 해시 검증이 CPU를 많이 쓰므로 10개씩 끊어서 요청)
export function loginUsers(count) {
  const tokens = [];
  for (let start = 1; start <= count; start += 10) {
    const requests = [];
    for (let i = start; i < start + 10 && i <= count; i++) {
      requests.push([
        'POST',
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ loginId: userLoginId(i), password: USER_PASSWORD }),
        { headers: JSON_HEADERS, tags: { name: 'POST /api/auth/login' } },
      ]);
    }
    const responses = http.batch(requests);
    for (const res of responses) {
      if (res.status !== 200) {
        fail(`로그인 실패(status=${res.status}). 먼저 seed를 실행했는지 확인하세요: ${res.body}`);
      }
      tokens.push(res.json('data.accessToken'));
    }
  }
  return tokens;
}

// 시드된 상영 일정(screeningId)과 상영관 좌석 수를 모두 모은다.
export function fetchScreenings() {
  const theaters = http.get(`${BASE_URL}/api/theaters`, { tags: { name: 'GET /api/theaters' } }).json('data.theaters');
  const screenings = [];
  for (const theater of theaters) {
    if (!theater.theaterName.startsWith('LT-')) {
      continue;
    }
    const res = http.get(`${BASE_URL}/api/theaters/${theater.theaterId}/screenings`, {
      tags: { name: 'GET /api/theaters/{theaterId}/screenings' },
    });
    for (const movie of res.json('data.movies')) {
      for (const screen of movie.screens) {
        for (const schedule of screen.schedules) {
          screenings.push({
            screeningId: schedule.screeningId,
            theaterId: theater.theaterId,
            totalSeats: schedule.availableSeats,
          });
        }
      }
    }
  }
  if (screenings.length === 0) {
    fail('시드된 상영 일정이 없습니다. 먼저 seed.js를 실행하세요.');
  }
  return { theaterIds: theaters.filter((t) => t.theaterName.startsWith('LT-')).map((t) => t.theaterId), screenings };
}

export function randomItem(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function checkOk(res, name) {
  return check(res, { [`${name} 200`]: (r) => r.status === 200 });
}
