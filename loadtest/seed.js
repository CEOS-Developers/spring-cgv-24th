// 부하테스트용 데이터 준비 스크립트 (여러 번 실행해도 안전: 이미 만든 데이터는 건너뜀)
//
//   ADMIN_TOKEN=<서버의 ADMIN_TOKEN 값> k6 run loadtest/seed.js
//
// 만드는 데이터
//   - 관리자 계정 1개 (lt_admin)
//   - 영화관 3개(LT-극장1~3) x 상영관 2개(일반/특별, 좌석 100개) = 상영관 6개
//   - 영화 10개(LT-영화1~10)
//   - 상영관마다 상영 일정 5개 = 30개 (모두 미래 시각이라 예매 가능)
//   - 일반 사용자 USERS명 (기본 100명, lt_user_1 ~ lt_user_100)
import http from 'k6/http';
import { check, fail } from 'k6';
import {
  BASE_URL,
  USER_COUNT,
  USER_PASSWORD,
  ADMIN_LOGIN_ID,
  JSON_HEADERS,
  userLoginId,
  login,
} from './lib/common.js';

export const options = { vus: 1, iterations: 1 };

const ADMIN_TOKEN = __ENV.ADMIN_TOKEN;
const THEATERS = 3;
const MOVIES = 10;
const SEATS_PER_SCREEN = 100;
const SCREENINGS_PER_SCREEN = 5;

function post(path, token, body) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: token ? { ...JSON_HEADERS, Authorization: `Bearer ${token}` } : JSON_HEADERS,
  });
}

function isoLocal(date) {
  // 서버는 시간대 정보 없는 LocalDateTime을 받는다. 서버 시계와의 오차를 피하려고 항상 2일 이후로 만든다.
  return date.toISOString().slice(0, 19);
}

function signup(loginId, name, adminToken) {
  const res = post('/api/auth/signup', null, {
    name,
    email: `${loginId}@loadtest.local`,
    loginId,
    password: USER_PASSWORD,
    adminToken: adminToken || null,
  });
  // 이미 있으면 409, 새로 만들면 2xx
  if (res.status >= 300 && res.status !== 409) {
    fail(`회원가입 실패 ${loginId}: ${res.status} ${res.body}`);
  }
}

function listLt(path, jsonPath, nameField) {
  const items = http.get(`${BASE_URL}${path}`).json(jsonPath);
  return items.filter((item) => item[nameField].startsWith('LT-'));
}

export default function () {
  if (!ADMIN_TOKEN) {
    fail('ADMIN_TOKEN 환경변수가 필요합니다. 예) ADMIN_TOKEN=... k6 run loadtest/seed.js');
  }

  // 1. 관리자 계정
  signup(ADMIN_LOGIN_ID, '부하테스트관리자', ADMIN_TOKEN);
  const adminJwt = login(ADMIN_LOGIN_ID, USER_PASSWORD);
  if (!adminJwt) {
    fail('관리자 로그인 실패');
  }

  // 2. 영화관·상영관·영화·상영 일정 (이미 있으면 건너뜀)
  let theaters = listLt('/api/theaters', 'data.theaters', 'theaterName');
  if (theaters.length === 0) {
    for (let t = 1; t <= THEATERS; t++) {
      check(post('/api/admin/theaters', adminJwt, { name: `LT-극장${t}`, address: `부하테스트 주소 ${t}` }), {
        '영화관 생성': (r) => r.status < 300,
      });
    }
    theaters = listLt('/api/theaters', 'data.theaters', 'theaterName');

    for (const theater of theaters) {
      const types = ['NORMAL', 'SPECIAL'];
      for (let s = 0; s < types.length; s++) {
        check(
          post(`/api/admin/theaters/${theater.theaterId}/screens`, adminJwt, {
            screenType: types[s],
            name: `${s + 1}관`,
            totalSeats: SEATS_PER_SCREEN,
          }),
          { '상영관 생성': (r) => r.status < 300 },
        );
      }
    }

    for (let m = 1; m <= MOVIES; m++) {
      check(post('/api/admin/movies', adminJwt, { title: `LT-영화${m}` }), { '영화 생성': (r) => r.status < 300 });
    }
    const movies = listLt('/api/movies', 'data.movies', 'title');

    // 상영관마다 영화를 돌려가며 상영 일정을 만든다. 같은 상영관 안에서는 시간이 겹치지 않게 3시간 간격.
    const base = new Date(Date.now() + 2 * 24 * 3600 * 1000);
    base.setUTCHours(1, 0, 0, 0);
    let movieIdx = 0;
    for (const theater of theaters) {
      const screens = http.get(`${BASE_URL}/api/theaters/${theater.theaterId}/screens`).json('data.screens');
      for (const screen of screens) {
        for (let k = 0; k < SCREENINGS_PER_SCREEN; k++) {
          const start = new Date(base.getTime() + k * 3 * 3600 * 1000);
          const end = new Date(start.getTime() + 2 * 3600 * 1000);
          const movie = movies[movieIdx % movies.length];
          movieIdx++;
          check(
            post('/api/admin/screenings', adminJwt, {
              movieId: movie.movieId,
              screenId: screen.screenId,
              startTime: isoLocal(start),
              endTime: isoLocal(end),
            }),
            { '상영 일정 생성': (r) => r.status < 300 },
          );
        }
      }
    }
    console.log(`상영 일정 시드 완료: 영화관 ${theaters.length}개`);
  } else {
    console.log(`이미 시드된 영화관 ${theaters.length}개가 있어 인프라 데이터 생성을 건너뜁니다.`);
  }

  // 3. 일반 사용자 (비밀번호 해시 때문에 요청당 CPU를 많이 쓰므로 10명씩 끊어서 동시 요청)
  for (let start = 1; start <= USER_COUNT; start += 10) {
    const requests = [];
    for (let i = start; i < start + 10 && i <= USER_COUNT; i++) {
      requests.push([
        'POST',
        `${BASE_URL}/api/auth/signup`,
        JSON.stringify({
          name: `부하테스트유저${i}`,
          email: `${userLoginId(i)}@loadtest.local`,
          loginId: userLoginId(i),
          password: USER_PASSWORD,
          adminToken: null,
        }),
        { headers: JSON_HEADERS },
      ]);
    }
    for (const res of http.batch(requests)) {
      if (res.status >= 300 && res.status !== 409) {
        fail(`사용자 생성 실패: ${res.status} ${res.body}`);
      }
    }
  }
  console.log(`사용자 ${USER_COUNT}명 준비 완료`);
}
