// 시나리오: 로그인(비밀번호 해시) 부하가 다른 API에 주는 영향
//
//   MODE=movies-only k6 run loadtest/login.js   # A. 기준선
//   k6 run loadtest/login.js                    # B. 로그인 + 영화 목록 동시
//   LOGIN_VUS=20 k6 run loadtest/login.js       # 로그인 VU 수 조절
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, JSON_HEADERS, USER_COUNT, USER_PASSWORD, userLoginId} from './lib/common.js';

const MODE = __ENV.MODE || 'both';
const LOGIN_VUS = parseInt(__ENV.LOGIN_VUS || '10');

const scenarios = {
    movies: {
        executor: 'constant-vus',
        exec: 'movies',          // 아래 export function movies 를 실행
        vus: 5,
        duration: '1m',
    },
};
if (MODE !== 'movies-only') {
    scenarios.login = {
        executor: 'constant-vus',
        exec: 'loginFlow',
        vus: LOGIN_VUS,
        duration: '1m',
    };
}

export const options = {
    scenarios,
    thresholds: {
        // 임계값을 비워 두면 요약에 이 태그의 p95가 따로 표시된다
        'http_req_duration{name:GET /api/movies}': [],
        'http_req_duration{name:POST /api/auth/login}': [],
    },
};

export function movies() {
    const res = http.get(`${BASE_URL}/api/movies`, { tags: { name: 'GET /api/movies' } });
    sleep(0.2);
    if (!check(res, { '영화 조회 200': (r) => r.status === 200 })) {
        return null;
    }
}

export function loginFlow() {
    // 같은 계정으로 동시에 로그인하면 토큰 삭제·저장이 서로 충돌할 수 있으므로
    // VU마다 자기 계정을 고정해서 쓴다.
    const loginId = userLoginId((__VU % USER_COUNT) + 1);

    const res = http.post(`${BASE_URL}/api/auth/login`, JSON.stringify({ loginId, password: USER_PASSWORD }), {
        headers: JSON_HEADERS,
        tags: { name: 'POST /api/auth/login' },
    });
    if (!check(res, { '로그인 200': (r) => r.status === 200 })) {
        return null;
    }
}