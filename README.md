# spring-cgv-24th
CEOS 24기 백엔드 스터디 - CGV 클론 코딩 프로젝트

## ERD

```mermaid
erDiagram
    users ||--o{ reservation : "예매한다"
    users ||--o{ purchase : "구매한다"
    users ||--o{ movie_like : "찜한다"
    users ||--o{ branch_like : "찜한다"
    users ||--o{ refresh_token : "발급받는다"
    branch ||--o{ theater : "보유한다"
    branch ||--o{ stock : "보유한다"
    branch ||--o{ purchase : "발생한다"
    branch ||--o{ branch_like : "찜된다"
    theater ||--o{ screening : "편성한다"
    movie ||--o{ screening : "상영된다"
    movie ||--o{ movie_like : "찜된다"
    screening ||--o{ reservation : "예매된다"
    screening ||--o{ reservation_seat : "좌석중복방지"
    reservation ||--o{ reservation_seat : "포함한다"
    product ||--o{ stock : "재고를가진다"
    product ||--o{ purchase_product : "포함된다"
    purchase ||--o{ purchase_product : "포함한다"

    users {
        bigint user_id PK
        varchar login_id UK
        varchar password
        varchar name
        date birth_date
        varchar email
        varchar phone_number
        enum role "USER / ADMIN"
    }
    branch {
        bigint branch_id PK
        varchar name
        varchar address
        enum region "지역 탭"
        enum status "운영 상태"
        text description "교통·주차 안내"
        varchar image_url "대표 이미지"
    }
    theater {
        bigint theater_id PK
        bigint branch_id FK
        enum theater_type "좌석 배치 보유"
        varchar name
    }
    movie {
        bigint movie_id PK
        varchar title
        varchar director
        varchar genre
        int running_time
        date release_date
        varchar age_rating
    }
    screening {
        bigint screening_id PK
        bigint theater_id FK
        bigint movie_id FK
        datetime start_at
        datetime end_at
        int price
    }
    reservation {
        bigint reservation_id PK
        bigint user_id FK
        bigint screening_id FK
        enum status "선점/확정/취소/만료"
        datetime expires_at "선점 만료 예정"
        datetime confirmed_at "결제 완료"
        datetime cancelled_at
    }
    reservation_seat {
        bigint reservation_seat_id PK
        bigint reservation_id FK
        bigint screening_id FK "유니크 제약용"
        int row_num
        int col_num
        enum audience_type "권종"
        int paid_price
        bigint release_key "유니크 제약용"
    }
    product {
        bigint product_id PK
        varchar name
        int price
    }
    stock {
        bigint stock_id PK
        bigint branch_id FK
        bigint product_id FK
        int quantity
    }
    purchase {
        bigint purchase_id PK
        bigint user_id FK
        bigint branch_id FK
        int total_price
        datetime purchased_at
    }
    purchase_product {
        bigint purchase_product_id PK
        bigint purchase_id FK
        bigint product_id FK
        int quantity
        int unit_price
    }
    movie_like {
        bigint movie_like_id PK
        bigint user_id FK
        bigint movie_id FK
    }
    branch_like {
        bigint branch_like_id PK
        bigint user_id FK
        bigint branch_id FK
    }
    refresh_token {
        bigint refresh_token_id PK
        bigint user_id FK
        char token_hash UK "SHA-256"
        char family_id "같은 로그인의 묶음"
        datetime expires_at
        datetime used_at "순환 시각"
        datetime revoked_at "폐기 시각"
    }
```

모든 테이블은 `BaseTimeEntity`를 상속해 `created_at` / `updated_at`을 가집니다. 그림에서는 생략했습니다.
`region`, `status`, `theater_type`, `audience_type`은 테이블이 아니라 **자바 ENUM**이며 `varchar`로 저장됩니다.

## 테이블 정의

<details>
<summary><strong>user (회원)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| user_id | bigint (PK) | 식별자 |
| login_id | varchar(50) | 로그인 ID, 유니크. 영문 소문자·숫자 4~20자 |
| password | varchar(255) | BCrypt 해시 |
| name | varchar(50) | 이름 |
| birth_date | date | 생년월일 |
| email | varchar(100) | 이메일. 로그인에 쓰지 않아 유니크가 아닙니다 |
| phone_number | varchar(11) | 하이픈 없이 숫자만 저장합니다 |
| role | varchar(20) | 권한 (ENUM `Role`: USER / ADMIN) |
| created_at | datetime | 가입일시 |

**`Role`** — 회원가입은 항상 `USER`로 생성합니다. 가입 요청에는 role 필드가 없고, 빌더도 `USER`로 고정합니다. `ADMIN`은 `User.createAdmin()`으로만 만들어지며, 로컬 환경에서 기동 시 환경변수로 받은 계정 하나를 생성합니다.

**관계**
- `reservation` 1:N — 회원 한 명이 예매를 여러 건 합니다
- `purchase` 1:N — 회원 한 명이 구매를 여러 건 합니다
- `movie_like` 1:N — 회원 한 명이 영화를 여러 개 찜합니다
- `branch_like` 1:N — 회원 한 명이 지점을 여러 개 찜합니다
- `refresh_token` 1:N — 회원 한 명이 로그인(기기)마다 리프레시 토큰 묶음을 하나씩 가집니다. 재발급할 때마다 묶음에 행이 하나씩 늘어납니다

</details>

<details>
<summary><strong>branch (영화관 지점)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| branch_id | bigint (PK) | 식별자 |
| name | varchar(100) | 지점명 (예: CGV 홍대) |
| address | varchar(255) | 주소 |
| region | varchar(30) | 지역 탭 값 (ENUM `Region`) |
| status | varchar(20) | 운영 상태 (ENUM `BranchStatus`) |
| description | text | 교통·주차 안내 서술문 |
| image_url | varchar(500) | 대표 이미지 1장, nullable |

**`Region`** — 극장 목록 상단의 지역 탭. 선언 순서가 곧 노출 순서입니다.

| 값 | 표시명 |
|---|---|
| SEOUL / GYEONGGI / INCHEON / GANGWON | 서울 / 경기 / 인천 / 강원 |
| DAEJEON_CHUNGCHEONG / DAEGU | 대전·충청 / 대구 |
| BUSAN_ULSAN / GYEONGSANG | 부산·울산 / 경상 |
| GWANGJU_JEOLLA / JEJU | 광주·전라 / 제주 |

**`BranchStatus`**

| 값 | 표시명 | 목록 노출 |
|---|---|---|
| OPEN | 운영중 | O (예매 가능) |
| TEMPORARILY_CLOSED | 임시휴업 | O (배지 표시) |
| CLOSED | 운영종료 | X (상세 조회는 가능) |

**관계**
- `theater` 1:N — 지점 하나에 상영관이 여러 개 있습니다
- `stock` 1:N — 지점 하나가 상품별 재고를 여러 개 보유합니다
- `purchase` 1:N — 지점 하나에서 구매가 여러 건 발생합니다
- `branch_like` 1:N — 지점 하나를 여러 회원이 찜합니다

</details>

<details>
<summary><strong>TheaterType (상영관 종류) — 테이블이 아닌 ENUM</strong></summary>

상영관 종류는 대분류(일반관/특별관) 아래에 실제 종류가 놓이는 2단 구조입니다.
대분류는 `TheaterCategory`, 실제 종류는 `TheaterType`이 갖습니다.

| 값 | 표시명 | 대분류 | rowCount | colCount | 총 좌석 |
|---|---|---|---|---|---|
| STANDARD | 일반관 | GENERAL | 8 | 10 | 80 |
| IMAX | IMAX | SPECIAL | 12 | 22 | 264 |
| FOUR_DX | 4DX | SPECIAL | 10 | 16 | 160 |
| SCREEN_X | SCREENX | SPECIAL | 10 | 20 | 200 |

`theater.theater_type`에 `varchar(20)`으로 저장됩니다. `TheaterCategory`는
`TheaterType`이 결정하는 값이라 컬럼으로 저장하지 않습니다.

좌석 배치를 이 ENUM이 보유합니다. "종류가 같으면 좌석이 동일하다"는
요구사항에 따라 상영관마다 배치를 저장하지 않습니다. 종류가 고정된 소수이고
자체 속성이 행·열 두 개뿐이라 별도 테이블로 둘 이유가 없었습니다.
좌석 범위 검증도 `TheaterType.isValidSeat()`로 이 ENUM이 책임집니다.

</details>

<details>
<summary><strong>theater (상영관)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| theater_id | bigint (PK) | 식별자 |
| branch_id | bigint (FK) | 소속 지점 |
| theater_type | varchar(20) | 상영관 종류 (ENUM `TheaterType`) |
| name | varchar(50) | 관 이름 (예: 3관) |

**관계**
- `branch` N:1 — 상영관 여러 개가 지점 하나에 속합니다
- `TheaterType` — 종류는 ENUM이므로 조인 없이 컬럼으로 들고 있습니다
- `screening` 1:N — 상영관 하나에서 회차가 여러 번 열립니다

</details>

<details>
<summary><strong>movie (영화)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| movie_id | bigint (PK) | 식별자 |
| title | varchar(200) | 제목 |
| director | varchar(100) | 감독 |
| genre | varchar(50) | 장르 |
| running_time | int | 상영 시간(분) |
| release_date | date | 개봉일 |
| age_rating | varchar(20) | 관람 등급 |

**관계**
- `screening` 1:N — 영화 하나가 여러 회차로 상영됩니다
- `movie_like` 1:N — 영화 하나를 여러 회원이 찜합니다

</details>

<details>
<summary><strong>screening (상영 회차)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| screening_id | bigint (PK) | 식별자 |
| theater_id | bigint (FK) | 상영관 |
| movie_id | bigint (FK) | 영화 |
| start_at | datetime | 상영 시작 시각 |
| end_at | datetime | 상영 종료 시각 |
| price | int | 해당 회차 가격 |

**관계**
- `theater` N:1 — 회차 여러 개가 상영관 하나에서 열립니다
- `movie` N:1 — 회차 여러 개가 같은 영화를 상영합니다
- `reservation` 1:N — 회차 하나에 예매가 여러 건 있습니다
- `reservation_seat` 1:N — 중복 예매 방지용으로 직접 참조합니다

예매의 실제 대상은 영화가 아니라 이 회차입니다. 가격을 영화가 아닌 회차에
둔 것은 조조·심야 등 상영 조건에 따라 가격이 달라질 수 있기 때문입니다.

</details>

<details>
<summary><strong>reservation (예매)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| reservation_id | bigint (PK) | 식별자 |
| user_id | bigint (FK) | 예매자 |
| screening_id | bigint (FK) | 예매한 회차 |
| status | varchar(20) | 예매 상태 (ENUM `ReservationStatus`) |
| expires_at | datetime | 선점 만료 예정 시각 (좌석 선택 + 10분) |
| confirmed_at | datetime (null) | 결제 완료 시각 |
| cancelled_at | datetime (null) | 취소 시각 |

**관계**
- `user` N:1 — 여러 예매가 회원 한 명에 속합니다
- `screening` N:1 — 여러 예매가 회차 하나에 묶입니다
- `reservation_seat` 1:N — 예매 한 건에 좌석이 여러 개 있습니다

한 번의 예매에 여러 좌석이 선택될 수 있으므로 헤더-디테일 구조로
분리했습니다. 이 테이블은 "예매 행위"를 나타냅니다.

좌석을 고른 시각은 별도 컬럼 없이 `created_at`이 갖습니다.

</details>

<details>
<summary><strong>ReservationStatus (예매 상태) — 테이블이 아닌 ENUM</strong></summary>

| 값 | 표시명 | 좌석 |
|---|---|---|
| PENDING | 결제대기 | 점유 |
| RESERVED | 예매완료 | 점유 |
| CANCELLED | 취소 | 해제 |
| EXPIRED | 선점만료 | 해제 |

| from | to | 트리거 |
|---|---|---|
| — | PENDING | 좌석 선택 (10분간 선점) |
| PENDING | RESERVED | 결제 성공 |
| PENDING | CANCELLED | 결제 실패 / 사용자 취소 |
| PENDING | EXPIRED | 만료 시각 경과 |
| RESERVED | CANCELLED | 취소, 상영 20분 전까지 |

결제에 실패하면 좌석을 바로 놓습니다. 실패한 자리를 붙들고 재시도하게 두면 경쟁이 심한
회차에서 좌석 회전이 막힙니다. 실제 CGV도 결제에 실패하면 좌석 선택부터 다시 진행합니다.

만료를 `CANCELLED`와 분리한 이유는 사용자가 놓은 것과 시간이 지나 회수한 것의 원인이
다르기 때문입니다. 합치면 "이 좌석이 왜 풀렸나"를 되짚을 수 없습니다.

</details>

<details>
<summary><strong>AudienceType (권종) — 테이블이 아닌 ENUM</strong></summary>

| 값 | 표시명 | 할인율 | 기준가 14,000 기준 |
|---|---|---|---|
| ADULT | 일반 | 0% | 14,000 |
| YOUTH | 청소년 | 20% | 11,200 |
| PREFERENTIAL | 우대 | 50% | 7,000 |
| SENIOR | 경로 | 50% | 7,000 |

기준가는 `screening.price`가 갖고 권종은 거기서 얼마를 깎는지만 압니다. 가격표를 테이블로
두지 않은 이유는 값이 고정된 소수이고 자체 속성이 할인율 하나뿐이기 때문입니다.
`TheaterType`이 좌석 배치를 갖는 것과 같은 판단입니다.

좌석마다 권종이 붙습니다. 화면은 인원을 먼저 고르지만, 좌석-권종 매핑이 없으면 좌석별
금액을 정할 수 없습니다. 실제 티켓에도 좌석마다 권종이 찍힙니다.

</details>

<details>
<summary><strong>reservation_seat (예매 좌석)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| reservation_seat_id | bigint (PK) | 식별자 |
| reservation_id | bigint (FK) | 소속 예매 |
| screening_id | bigint (FK) | 회차 (유니크 제약용 중복 저장) |
| row_num | int | 좌석 행 위치 |
| col_num | int | 좌석 열 위치 |
| audience_type | varchar(20) | 권종 (ENUM `AudienceType`) |
| paid_price | int | 권종 할인이 적용된 결제 시점 가격 |
| release_key | bigint | 점유 중 0, 풀린 좌석은 자기 reservation_id |

**제약**: (screening_id, row_num, col_num, release_key) 유니크 — 중복 예매 방지

**관계**
- `reservation` N:1 — 여러 좌석이 예매 한 건에 속합니다
- `screening` N:1 — 유니크 제약을 위해 부모의 screening_id를 중복 저장합니다

좌석 테이블이 없으므로 위치를 행·열 숫자로 저장합니다.
`paid_price`는 회차 가격이 변경되어도 과거 결제 금액이 유지되도록
예매 시점 값을 복사한 것입니다.

취소·만료된 좌석도 행을 지우지 않고 `release_key`만 세웁니다.
어느 좌석을 얼마에 잡았는지가 남습니다.

</details>

<details>
<summary><strong>product (매점 상품)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| product_id | bigint (PK) | 식별자 |
| name | varchar(100) | 상품명 |
| price | int | 정가 |

**관계**
- `stock` 1:N — 상품 하나가 지점별 재고를 가집니다
- `purchase_product` 1:N — 상품 하나가 여러 구매 항목에 포함됩니다

"모든 영화관의 매점 메뉴는 같아요"에 따라 전 지점 공통으로 하나씩 존재합니다.

</details>

<details>
<summary><strong>stock (재고)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| stock_id | bigint (PK) | 식별자 |
| branch_id | bigint (FK) | 지점 |
| product_id | bigint (FK) | 상품 |
| quantity | int | 수량 |

**제약**: (branch_id, product_id) 유니크

**관계**
- `branch` N:1 — 여러 재고 항목이 지점 하나에 속합니다
- `product` N:1 — 여러 재고 항목이 상품 하나를 가리킵니다

지점과 상품의 N:M 관계를 푼 중간 테이블입니다. `quantity`라는 부가 속성이
있으므로 단순 연결이 아닌 독립 엔티티로 다룹니다.

</details>

<details>
<summary><strong>purchase (매점 구매)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| purchase_id | bigint (PK) | 식별자 |
| user_id | bigint (FK) | 구매자 |
| branch_id | bigint (FK) | 구매 지점 |
| total_price | int | 총 결제 금액 |
| purchased_at | datetime | 구매 시각 |

**관계**
- `user` N:1 — 여러 구매가 회원 한 명에 속합니다
- `branch` N:1 — 여러 구매가 지점 하나에서 발생합니다
- `purchase_product` 1:N — 구매 한 건에 상품 항목이 여러 개 있습니다

예매와 동일한 헤더-디테일 구조입니다. 환불이 없으므로 상태 컬럼을 두지 않았습니다.

</details>

<details>
<summary><strong>purchase_product (구매 상품)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| purchase_product_id | bigint (PK) | 식별자 |
| purchase_id | bigint (FK) | 소속 구매 |
| product_id | bigint (FK) | 상품 |
| quantity | int | 수량 |
| unit_price | int | 구매 시점 단가 |

**관계**
- `purchase` N:1 — 여러 상품 항목이 구매 한 건에 속합니다
- `product` N:1 — 여러 구매 항목이 상품 하나를 가리킵니다

</details>

<details>
<summary><strong>movie_like (영화 찜)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| movie_like_id | bigint (PK) | 식별자 |
| user_id | bigint (FK) | 회원 |
| movie_id | bigint (FK) | 영화 |
| created_at | datetime | 찜한 시각 |

**제약**: (user_id, movie_id) 유니크 — 중복 찜 방지

**관계**
- `user` N:1 — 여러 찜 항목이 회원 한 명에 속합니다
- `movie` N:1 — 여러 찜 항목이 영화 하나를 가리킵니다

</details>

<details>
<summary><strong>branch_like (지점 찜)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| branch_like_id | bigint (PK) | 식별자 |
| user_id | bigint (FK) | 회원 |
| branch_id | bigint (FK) | 지점 |
| created_at | datetime | 찜한 시각 |

**제약**: (user_id, branch_id) 유니크

**관계**
- `user` N:1 — 여러 찜 항목이 회원 한 명에 속합니다
- `branch` N:1 — 여러 찜 항목이 지점 하나를 가리킵니다

</details>

<details>
<summary><strong>refresh_token (리프레시 토큰)</strong></summary>

| 컬럼 | 타입 | 설명 |
|---|---|---|
| refresh_token_id | bigint (PK) | 식별자 |
| user_id | bigint (FK) | 회원 |
| token_hash | char(64) | 토큰 원문의 SHA-256 해시. 원문은 저장하지 않습니다 |
| family_id | char(36) | 같은 로그인에서 순환으로 이어진 토큰의 묶음 id(UUID). 로그인할 때 만들고 재발급한 토큰이 물려받습니다 |
| expires_at | datetime | 만료 시각 (로그인 시각 + `REFRESH_TOKEN_VALIDITY`). 재발급한 토큰도 같은 값을 물려받습니다 |
| used_at | datetime | 이 토큰으로 재발급받은 시각. 값이 있는 토큰이 다시 오면 재사용입니다 |
| revoked_at | datetime | 로그아웃이나 재사용 탐지로 폐기된 시각. null이면 폐기되지 않았습니다 |
| created_at | datetime | 발급 시각 |

**제약**: token_hash 유니크 — 받은 토큰을 해시해 한 행으로 바로 찾습니다
**인덱스**: `idx_refresh_token_family(family_id)` — 로그아웃·재사용 탐지 때 묶음 전체를 폐기하는 조회 조건입니다

**관계**
- `user` N:1 — 여러 토큰이 회원 한 명에 속합니다. 로그인마다 행이 하나씩 생겨 기기별로 따로 유지됩니다

</details>

---

## 다대다 관계 처리

개념적으로 N:M인 관계는 모두 중간 테이블로 분해했습니다.

| 개념적 관계 | 중간 테이블 | 부가 속성 |
|---|---|---|
| 회원 ↔ 영화 | `movie_like` | created_at |
| 회원 ↔ 지점 | `branch_like` | created_at |
| 지점 ↔ 상품 | `stock` | quantity |
| 구매 ↔ 상품 | `purchase_product` | quantity, unit_price |

JPA의 `@ManyToMany`는 조인 테이블에 부가 속성을 둘 수 없고 생성되는
쿼리를 예측하기 어렵습니다. 위 네 관계 모두 부가 속성이 필요하므로
중간 테이블을 독립 엔티티로 정의했습니다.

---

## 설계 판단 과정

### 용어 분리: 지점 vs 상영관

요구사항의 "영화관"이 두 의미로 쓰이고 있었습니다. CGV 홍대점 같은 **지점**(`branch`)과 그 안에서 실제로 영화를 트는 **상영관**(`theater`)을 한 테이블로 묶으면 이후 관계가 전부 꼬입니다. 특별관·일반관은 속성이 동일하므로 테이블을 나누지 않고 `theater_type`으로 종류를 구분했습니다. 이때 "특별관"은 IMAX·4DX·SCREENX를 묶는 대분류이지 종류 자체가 아니므로, 대분류는 `TheaterCategory`로 따로 두고 배치는 실제 종류가 갖습니다.

### 지점 목록·상세 화면에서 역산한 컬럼

초안의 `branch`는 `name`, `address` 두 개뿐이었습니다. 멘토 피드백(지역 / 운영 여부 / 설명 / 이미지)을 받고 실제 CGV 화면을 다시 보니, 빠진 것이 단순히 "표시할 값"이 아니라 **목록 화면의 필터·검색 축 자체**였습니다. 지역 탭도 검색창도 걸 컬럼이 없었습니다. 네 개를 각각 이렇게 결정했습니다.

#### 지역 — ENUM (테이블 아님)

탭 값은 `서울 / 경기 / 인천 / 강원 / 대전·충청 / 대구 / 부산·울산 / 경상 / 광주·전라 / 제주`입니다. 행정구역과 1:1이 아닙니다. "대전·충청"은 1광역시+3도를 묶은 것이고 "경상"은 대구·부산·울산을 뺀 나머지입니다.

처음에는 **행정구역이 아니니까 오히려 테이블이 필요한 것 아닌가** 생각했는데, 반대였습니다. 행정구역이라면 외부 표준 데이터를 따라가야 하므로 테이블이 맞습니다. 하지만 이 값은 CGV가 임의로 정한 묶음이라 **값의 소유자가 코드**입니다. 10개 내외에서 멈추고, 표시명과 정렬 순서 말고는 자체 속성도 없습니다.

| | ENUM (채택) | `region` 테이블 |
|---|---|---|
| 조회 | 조인 없음 | 목록 조회마다 조인 |
| 안전성 | 컴파일 타임에 오타가 잡힘 | 런타임 FK |
| 정렬 | 선언 순서 = 탭 순서 (컬럼 불필요) | `sort_order` 컬럼 필요 |
| 값 변경 | **배포 필요** | 운영자가 실시간 관리 |
| 지역명 검색 | **2단계** (아래) | SQL 한 방 |
| 확장 | 하위 지역·배너 붙이려면 이관 | 컬럼 추가로 끝 |

트레이드오프는 **지역명 검색**에서 나옵니다. DB에는 `BUSAN_ULSAN`만 있고 `"부산·울산"`은 코드에만 있으므로, 검색어를 먼저 `Region`으로 역변환한 뒤 `IN` 절에 넣어야 합니다.

```java
// Region.java — 표시명이 DB에 없으므로 키워드를 먼저 Region으로 바꾼다
public static List<Region> searchByKeyword(String keyword) {
    return Arrays.stream(values())
            .filter(region -> region.displayName.contains(keyword))
            .toList();
}
```

```java
// BranchRepository.java — 지점명 LIKE와 지역 IN을 OR로 묶는다
@Query("""
        SELECT b FROM Branch b
        WHERE b.status <> :excluded
          AND (b.name LIKE %:keyword% OR b.region IN :regions)
        ORDER BY b.region, b.name
        """)
List<Branch> searchByKeyword(@Param("keyword") String keyword,
                             @Param("regions") List<Region> regions,
                             @Param("excluded") BranchStatus excluded);
```

검색이 두 단계가 되는 비용을 치르고 조인과 참조 테이블 관리 비용을 덜어낸 선택입니다. 지역별 배너·하위 지역 같은 속성이 생기면 그때 테이블로 승격하면 되고, 이관은 10행 INSERT입니다.

#### 운영 여부 — boolean이 아니라 ENUM

`is_operating boolean`으로 충분한지 먼저 따져봤는데, **실제 상태가 두 개가 아니었습니다.** 운영중 / 임시휴업(리모델링) / 운영종료(폐관)가 모두 존재합니다.

결정적인 건 셋의 취급이 다르다는 점입니다. **임시휴업은 배지를 달고 목록에 남지만, 폐관은 목록에서 빠집니다.** boolean이면 `false`가 이 둘을 구분하지 못해 "목록에서 뺄지 말지"를 판단할 근거가 사라집니다. 표현력 부족이 실제 로직을 막는 겁니다.

부수적으로, 상태가 늘어날 때 boolean은 컬럼 추가(스키마 변경)지만 ENUM은 값 추가 한 줄입니다. CGV에 실재하는 "오픈예정"이 그 경우인데, 지금 화면 요구에 없어 넣지 않았습니다.

목록에서 제외할 상태는 서비스 상수로 한 곳에만 둡니다.

```java
// BranchService.java
private static final BranchStatus EXCLUDED_FROM_LIST = BranchStatus.CLOSED;
```

상세 조회는 상태로 거르지 않습니다. 폐관 지점 링크로 들어와도 "운영종료"를 보여주는 편이 404보다 낫다고 봤습니다.

#### 영화관 설명 — TEXT

실제 데이터를 먼저 봤습니다. 광주금남로점 기준으로 버스 노선 나열 + 지하철 출구 + 주차장 목록 + 정산 방법이 **줄바꿈과 불릿을 포함해 수백~2천 자**이고, 극장마다 내용이 완전히 다릅니다.

| 후보 | 판단 |
|---|---|
| `varchar(255)` | **불가.** 한 문단도 안 들어갑니다 |
| `varchar(2000)` | 가능하지만 utf8mb4에서 8000바이트를 행 크기 예산(65535B)에서 선점합니다. 인덱싱할 일도 없는 컬럼이 자리를 차지합니다 |
| **`text` (채택)** | 64KB. 값을 행 밖에 두므로 목록 조회 부담이 작습니다 |
| `@Lob` | **회피.** MySQL에서 `LONGTEXT`(4GB)로 매핑되고 Hibernate가 LOB 스트림 처리를 시도합니다. 실제 분량에 비해 과합니다 |

```java
// Branch.java
@Column(columnDefinition = "TEXT")
private String description;
```

**저장 형식**은 줄바꿈을 포함한 평문 그대로입니다. HTML을 저장하면 XSS 방어 책임이 서버로 넘어오므로, 렌더링은 클라이언트가 `white-space: pre-line`으로 처리하도록 남겨둡니다.

컬럼 타입만큼 중요한 게 **어디에 실어 보내느냐**입니다. `description`은 `BranchDetailResponse`에만 넣고 목록 DTO에서는 뺐습니다. 지점 30개 목록에 2KB씩 붙이면 응답이 60KB가 되는데, 목록 카드는 그 값을 쓰지 않습니다.

#### 이미지 URL — 컬럼 1개

상세 페이지 상단에 대표 이미지가 **정확히 1장**이고, 순서·캡션·타입 같은 부가 속성이 없습니다. 부가 속성 없는 1:N은 테이블로 뺄 이유가 없습니다. `stock`과 `purchase_product`를 독립 엔티티로 둔 기준(부가 속성이 있으니까)을 그대로 뒤집어 적용한 것입니다.

결정적이었던 건 **나중 비용이 낮다**는 점입니다. 갤러리 요구가 생기면 이관이 한 줄입니다.

```sql
INSERT INTO branch_image (branch_id, url, sort_order)
SELECT branch_id, image_url, 1 FROM branch WHERE image_url IS NOT NULL;
```

반대로 지금 테이블을 만들면 모든 상세 조회에 조인이 붙고, "대표 1장을 어떻게 고르나"(`is_main` 플래그? `sort_order = 1`?)라는 문제가 즉시 생깁니다. 요구가 없는데 먼저 치를 비용입니다.

`varchar(500)`은 CDN 경로에 쿼리스트링이 붙는 경우까지 고려한 길이이고, 이미지 미등록 지점을 허용하려고 nullable로 뒀습니다.

#### 특별관 라벨은 컬럼이 아니라 집계값

목록 카드의 `SCREENX`, `4DX` 같은 라벨은 지점 속성이 아니라 **그 지점이 보유한 상영관들의 타입을 집계한 값**입니다. `branch`에 컬럼으로 넣으면 `theater`와 이중 관리가 되어 반드시 틀어집니다.

다만 지점마다 조회하면 N+1이므로 `IN` + `GROUP BY`로 한 번에 집계합니다.

```java
// TheaterRepository.java
@Query("""
        SELECT t.branch.id AS branchId, t.theaterType AS theaterType
        FROM Theater t
        WHERE t.branch.id IN :branchIds
        GROUP BY t.branch.id, t.theaterType
        """)
List<BranchTheaterType> findTheaterTypesByBranchIds(@Param("branchIds") List<Long> branchIds);
```

지점이 몇 개든 목록 조회는 쿼리 2방(지점 1 + 라벨 집계 1)입니다. 대분류가 `GENERAL`인 종류는 라벨에서 빼기 때문에 일반관만 있는 지점은 라벨이 비는데, 실제 화면에서 라벨 없는 극장이 있는 것과 일치합니다. 한 지점이 특별관을 여러 종류 보유할 수 있어 라벨 순서는 `TheaterType` 선언 순서로 고정했습니다.

### 좌석 설계

"종류가 같다면 좌석은 동일해요"와 "직사각형, 중간에 비어있는 곳 없음" 두 단서로 설계를 결정했습니다. 좌석 테이블을 만들지 않고 `theater_type`에 `row_count`, `col_count`만 두었습니다. 지점 30개 × 상영관 8개여도 배치는 종류 수만큼만 저장됩니다.

트레이드오프: 범위 벗어난 좌석을 DB가 막지 못하므로 `TheaterType.isValidSeat()`로 애플리케이션에서 검증합니다. 좌석별 속성이 생기면 구조 변경이 필요합니다.

### 예매/구매를 두 테이블로 분리

한 번의 예매에 여러 좌석이 선택됩니다. 단일 테이블로 좌석마다 한 행씩 저장하면 같은 사람이 같은 회차를 두 번 나눠 예매한 경우를 구분하지 못해 취소 처리가 불가능합니다.

- `reservation` — 예매 행위 (누가, 언제, 어느 회차, 상태)
- `reservation_seat` — 선택한 좌석들 (행, 열, 가격)

매점 구매도 같은 상황이므로 `purchase` / `purchase_product`에 동일 구조를 적용했습니다.

### 가격을 시점별로 복사 저장

정가는 `screening.price`, `product.price`에 두되, 결제 시점 금액은 `reservation_seat.paid_price`, `purchase_product.unit_price`에 복사합니다. 원본 가격을 UPDATE하면 과거 결제 기록의 금액까지 바뀌기 때문입니다. 정규화 관점의 중복이지만 **과거 사실은 변하지 않아야 한다**는 원칙을 우선했습니다.

### 중복 예매 방지

`reservation_seat`에 유니크 제약을 걸었습니다. 애플리케이션 로직만으로는 동시 요청을 완전히 막을 수 없어 DB 차원의 최종 안전망이 필요합니다. 검사와 INSERT 사이의 틈은 원리적으로 막을 수 없고, 그 틈을 제약이 막습니다. 비관적 락은 쓰지 않습니다. 좌석 단위로 잠글 행이 없고(좌석 마스터 테이블이 없습니다), 회차 행을 잠그면 회차 단위로 직렬화되어 처리량이 크게 떨어집니다.

결제 전 선점(`PENDING`)도 좌석 행을 실제로 만듭니다. 그래야 같은 제약이 그대로 선점 잠금 역할을 합니다. 실제 CGV도 결제 전 선택 단계에서 남이 그 좌석을 잡지 못합니다.

이 제약은 취소와 충돌합니다. 취소된 좌석 행이 남으면 다른 사람이 같은 자리를 예매할 때 제약에 걸립니다. 행을 지우면 충돌은 풀리지만 어느 좌석을 취소했는지가 사라집니다. 그래서 **유니크 키에 `release_key`를 넣어 점유 중인 행만 유일**하게 만들었습니다.

```
UNIQUE (screening_id, row_num, col_num, release_key)

점유 중   release_key = 0
풀린 좌석 release_key = 자기 reservation_id
```

MySQL에 partial unique index가 없어 "점유 중인 행만 유일"을 직접 표현할 수 없습니다. 해제 값으로 `reservation_id`를 쓰면 한 예매가 같은 좌석을 두 번 가질 수 없으므로 풀린 행끼리 충돌하지 않습니다. 시각을 쓰면 같은 좌석이 동시에 해제될 때 충돌할 수 있습니다.

### 선점 만료

`reservation.expires_at`에 만료 예정 시각을 두고, 별도 스케줄러 없이 두 지점에서 처리합니다.

- **조회**는 시각 조건으로 거릅니다. 만료된 선점은 행이 남아 있어도 점유로 세지 않습니다.
- **좌석을 잡기 직전**에 요청한 좌석을 막고 있는 만료 선점만 실제로 해제합니다. 유니크 인덱스는 만료 시각을 모르므로, 행을 놓아주지 않으면 시간이 지난 좌석도 다시 잡을 수 없습니다. 회차 전체가 아니라 요청한 좌석으로 좁힌 이유는 아래 「좌석 경합 — 락의 범위와 순서」에 있습니다.
- **해제는 조건부 UPDATE로 합니다.** `status = PENDING`인 예매만 `EXPIRED`로, `release_key = 0`인 좌석만 해제 값으로 바꿉니다. 두 사람이 같은 만료 선점을 동시에 풀 때, 늦은 쪽의 UPDATE는 0행이 되고 아무것도 건드리지 않습니다. 조건 없이 같은 값으로 다시 덮어쓰면 InnoDB는 새 행 버전을 만들지 않습니다. 그러면 늦은 쪽의 스냅샷에서 예매는 `EXPIRED`인데 좌석은 `release_key = 0`인 엇갈린 상태가 보이고, 빈 좌석을 "이미 선택된 좌석"으로 거절했습니다. MySQL 컨테이너 테스트에서 간헐적으로 드러나 실험으로 원인을 확정했습니다(H2에서는 재현되지 않습니다).

정확성은 이 두 경로로 보장되므로 스케줄러는 "언젠가 정리된다"는 보조 수단일 뿐입니다. 시간 제어·테스트 비용만 늘어난다고 보고 넣지 않았습니다.

### 좌석 경합 — 락의 범위와 순서

유니크 제약에 동시성을 맡기면 경합이 붙었을 때 두 가지가 문제가 됩니다. **락을 어디까지 거는가(범위)**와 **어떤 순서로 거는가(순서)**입니다. 제약은 "같은 좌석이 두 번 팔리지 않는다"는 정확성만 지켜주고 이 둘은 지켜주지 않습니다.

#### 범위 — B3를 사려고 J12에 락을 걸던 문제

IMAX 1관은 12행 × 22열, 264석입니다. 만료된 선점 3건(A1 / E5 / J12)이 행으로 남아 있는 상태에서 민수가 **B3**를, 지연이 **J12**를 동시에 고른다고 합시다. 두 사람이 고른 좌석은 겹치지 않습니다.

처음에는 좌석을 잡기 직전에 **그 회차의** 만료 선점을 전부 해제했습니다. 민수의 트랜잭션이 A1·E5·J12를 모두 UPDATE하고 그 락을 **커밋할 때까지** 쥡니다. 지연은 J12 행 앞에서 민수를 기다립니다. 민수는 B3를 사려고 J12에 락을 건 셈입니다. 좌석이 264개인데 **경합 지점은 사실상 1곳**이었습니다.

그래서 정리 대상을 요청한 좌석으로 좁혔습니다. (행, 열) 쌍을 IN 절에 넣는 방법이 DB마다 달라 스칼라 하나로 접었습니다. 열 수는 상영관 종류 최대가 22라 100진 자리에서 겹치지 않습니다.

```sql
and exists(select 1 from reservation_seat rs1_0
  where rs1_0.reservation_id = r1_0.reservation_id
    and rs1_0.screening_id = ?
    and rs1_0.release_key = 0
    and ((rs1_0.row_num * 100) + rs1_0.col_num) in (?))
```

민수는 `[203]`(B3 = 2×100+3)으로 조회해 해당하는 만료 선점이 없으니 **UPDATE 0건, 락 0개**입니다. 지연은 `[1012]`(J12)로 자기에게 필요한 것만 풉니다. 두 사람은 서로를 만나지 않습니다.

A1과 E5를 둘 다 만료시킨 뒤 A1만 요청해 확인했습니다.

| 예매 | 좌석 | 요청 후 status | `updated_at` |
|---|---|---|---|
| 1 | A1 | PENDING → **EXPIRED** | 갱신됨 |
| 2 | E5 | **PENDING 그대로** | **요청 전과 소수점까지 동일** |

만료 시각이 지났어도 요청하지 않은 좌석은 읽지도 쓰지도 않습니다.

#### 순서 — 커플석 데드락

민수와 지연이 나란한 A1·A2를 동시에 노립니다. 민수는 화면에서 A1을 먼저 탭했고 지연은 A2를 먼저 탭했습니다. 요청 배열의 순서가 그대로 INSERT 순서가 되고, **INSERT 순서가 곧 락 획득 순서**입니다.

```
[정렬 전]
t1  민수  INSERT A1 → 획득
t2  지연  INSERT A2 → 획득
t3  민수  INSERT A2 → 지연이 쥠 → 대기 ┐
t4  지연  INSERT A1 → 민수가 쥠  → 대기 ┘  순환 → MySQL이 한쪽을 강제 종료 (1213)

[정렬 후] 둘 다 [A1, A2]
t1  민수  INSERT A1 → 획득
t2  지연  INSERT A1 → 민수 대기         (여기서 멈춥니다. A2는 건드리지 않습니다)
t3  민수  INSERT A2 → 획득 → 커밋
t4  지연  → 1062 중복 키 → 409 "이미 선택된 좌석입니다"
```

데드락은 순서가 엇갈릴 때만 생기므로, 모두에게 같은 순서를 강제하면 순환 자체가 불가능해집니다. 좌석을 (행, 열) 오름차순으로 정렬해 INSERT합니다.

여기에는 두 번째 문제가 딸려 있었습니다. 1213은 Spring에서 `DeadlockLoserDataAccessException`으로 번역되는데 이는 `ConcurrencyFailureException` 계열이라 `DataIntegrityViolationException` catch에 **걸리지 않습니다.** 사용자는 409 대신 **500**을 받았습니다. `SEAT_RESERVATION_CONFLICT`를 추가해 409로 매핑했습니다. 락 대기 타임아웃은 "좌석이 팔렸다"가 아니라 "판정하지 못했다"는 뜻이라 `SEAT_ALREADY_RESERVED`와 구분합니다.

정렬을 빼고 엇갈린 요청 6건을 동시에 던지면 **성공이 0건**입니다. 데드락 희생자는 통째로 롤백되고, 여러 순환 고리가 동시에 생기면 살아남을 뻔한 트랜잭션까지 끌려 들어갑니다. 좌석은 비어 있는데 아무도 사지 못합니다.

#### 왜 이것이 커넥션 문제로 번지는가

InnoDB는 중복 키 INSERT를 즉시 거절하지 않습니다. 선행 트랜잭션이 끝날 때까지 S 락을 걸고 **블로킹**합니다. 그동안 그 스레드는 **커넥션을 쥔 채** 기다립니다.

기본값은 `innodb_lock_wait_timeout` 50초, Hikari 풀 10개, `connection-timeout` 30초였습니다. 좌석 하나의 경합으로 커넥션 10개가 묶이면 11번째 요청부터는 엔드포인트와 무관하게 죽습니다. 락 한도가 커넥션 한도보다 길어 **경합과 상관없는 API가 경합 중인 API보다 먼저 죽는** 역전까지 있었습니다.

락 한도를 3초로 줄이고 풀을 20, `connection-timeout`을 3초로 맞췄습니다. MySQL 세션이 좌석 행을 12초간 쥔 상태에서 같은 좌석을 요청하면 **3.32초** 만에 409가 돌아옵니다. 같은 좌석으로 동시 30건을 던지는 동안에도 무관한 `/api/movies`는 **200, 8~9ms**를 유지했고, 30건의 결과는 **201 정확히 1건 + 409 29건**이었습니다.

---

## 한계 및 범위 밖으로 둔 것

### 구조적 한계

- **좌석 범위 검증**: 상영관 크기를 벗어난 좌석 예매를 DB가 막지 못합니다.
  좌석을 개별 행으로 저장하지 않은 선택의 결과이며, 애플리케이션에서
  `theater_type`의 행·열과 비교해야 합니다.
- **`release_key`의 의미**: 0이 "점유 중"을 뜻하는 것은 도메인 언어가 아니라
  유니크 제약을 위한 장치입니다. 컬럼만 보고는 뜻을 알 수 없어 주석이 필요합니다.
- **만료된 선점 행**: 만료 시각이 지나도 그 좌석을 요청하는 선점 요청이 오기 전까지는 행이
  `release_key = 0`인 채로 남습니다. 조회는 시각 조건으로 거르므로 점유로 세지는
  않지만, 테이블만 보면 풀린 좌석인지 바로 드러나지 않습니다.
- **회차 정보 중복**: `reservation_seat.screening_id`는 부모의 값과 항상
  일치해야 하지만 DB가 이를 보장하지 않습니다. 애플리케이션이 지켜야 합니다.
- **나이 제한 검증**: `movie.age_rating`과 생년월일 비교는 애플리케이션
  책임입니다.
- **지역 값 변경에 배포 필요**: `Region`을 ENUM으로 둔 대가입니다. 지점이
  새 지역에 생기면 코드를 고쳐야 합니다.
- **하위 지명으로는 지역 탭이 검색되지 않음**: `"충남"`으로 `"대전·충청"`이
  잡히지 않습니다. 표시명 부분일치 방식의 한계입니다. 별칭 목록을 ENUM에
  들려주면 해결되지만, 그 목록이 길어지면 테이블로 옮길 신호입니다.
- **설명 전문 검색 불가**: `description`은 `LIKE` 풀스캔 외에 검색 수단이
  없습니다. "주차 가능한 지점 찾기" 같은 요구가 생기면 전문 인덱스나
  구조화된 편의시설 테이블이 필요합니다.
- **대표 이미지 1장 제한**: 갤러리가 필요해지면 `branch_image` 테이블로
  이관해야 합니다.

### 의도적으로 제외한 것

- **잔여 좌석 수**: 실제 CGV는 회차별 잔여 좌석을 표시합니다. 매번
  계산하면 목록 조회 시 부담이 있어 반정규화 컬럼을 두는 방식이 일반적이나,
  예매·취소마다 정확한 갱신이 필요하고 동시성 문제가 따릅니다.
- **다중 장르·출연진**: 실제로는 영화 하나에 여러 장르와 다수의 배우가
  있으나 별도 테이블이 필요합니다. 현재는 `genre` 단일 컬럼으로 두었습니다.
- **좌석 등급, 할인·쿠폰, 결제 수단, 리뷰·평점**: 요구사항 범위 밖입니다.

### 지점에 더 필요해 보이지만 이번에 넣지 않은 것

피드백 4개를 반영하면서 같이 눈에 띈 것들입니다. 지금 화면 요구로는 정당화되지 않아 기록만 해둡니다.

| 후보 | 왜 필요할 수 있나 | 왜 지금은 아닌가 |
|---|---|---|
| `tel varchar(20)` | CGV 상세에 지점 연락처가 표시됨 | 넣어도 무방한 수준이지만 피드백 범위 밖 |
| 주소 분해 (도로명 / 상세 / 법정동) | 실제 표기가 3조각이고 검색 정확도가 다름 | 과제 범위에선 한 컬럼이 다루기 쉬움 |
| `opened_on` / `closed_on` | `status`는 *지금* 상태만 담고 전환 시점을 남기지 않음 | "오픈예정"을 도입할 때 같이 필요해짐 |
| 지점 코드 (CGV 내부 극장코드) | 외부 연동·딥링크·URL slug에서 PK 노출을 피함 | 연동 대상이 없음 |
| 이미지를 full URL이 아닌 key로 | CDN 도메인이 바뀌면 전 행 UPDATE | 단순함을 택함. 도메인 교체는 한 번의 UPDATE로 감당 가능 |
| 위경도 `decimal(10,7)` × 2 | "가까운 극장" 정렬 | 이번 범위 밖으로 명시됨 |

---

## 추가 질문 정리

### Dirty Checking은 왜 모든 컬럼을 UPDATE할까요 — @DynamicUpdate

Dirty Checking이 변경을 감지하면 변경된 필드만 골라 UPDATE하는 것이 아니라 **엔티티의 모든 컬럼**을 포함한 UPDATE 문을 실행합니다. JPA 구현체가 엔티티마다 단 하나의 고정된 UPDATE 쿼리를 미리 캐싱해두기 때문입니다. 매번 변경된 컬럼만 담은 쿼리를 만들면 DB의 prepared statement 캐시를 활용하기 어렵습니다.

컬럼 수가 많고 일부 컬럼만 자주 바뀌는 엔티티라면 `@DynamicUpdate`로 개선할 수 있습니다.

```java
@Entity
@DynamicUpdate
public class Reservation extends BaseTimeEntity { ... }
```

`@DynamicUpdate`를 붙이면 변경된 컬럼만 포함한 UPDATE 문을 생성합니다. 단, 쿼리가 매번 달라지므로 prepared statement 캐시 효율이 떨어집니다. 컬럼 수가 적거나 대부분 컬럼이 함께 바뀌는 엔티티라면 기본 동작이 더 효율적입니다.

### Flush가 발생하는 시점

Flush는 영속성 컨텍스트의 변경 사항을 DB에 반영하는 작업입니다. 트랜잭션 commit과 달리 flush 자체는 DB에 쿼리를 보내는 것일 뿐, 트랜잭션은 아직 유지됩니다.

발생 시점은 세 가지입니다.

1. **`em.flush()` 직접 호출** — 명시적으로 즉시 flush합니다.
2. **트랜잭션 commit 시** — commit 직전에 자동 flush 후 commit합니다.
3. **JPQL 쿼리 실행 직전** — FlushMode가 `AUTO`(기본값)일 때, JPQL 실행 전 영속성 컨텍스트와 DB의 정합성을 맞추기 위해 자동 flush합니다.

`@Transactional(readOnly = true)`는 내부적으로 FlushMode를 `MANUAL`로 설정합니다. 읽기 전용 트랜잭션에서는 변경이 없으므로 flush 자체를 막아 불필요한 dirty checking 비용을 제거합니다.

### 영속성 컨텍스트 · 엔티티 매니저 · 트랜잭션은 항상 1:1로 대응할까요

항상 1:1은 아닙니다. Spring JPA의 기본 전략은 **트랜잭션 범위 영속성 컨텍스트**입니다. 트랜잭션 하나가 시작되면 영속성 컨텍스트가 하나 생성되고, 트랜잭션이 끝나면 영속성 컨텍스트도 종료됩니다. 이 범위 안에서는 같은 식별자로 조회하면 항상 같은 인스턴스를 반환합니다(1차 캐시).

엔티티 매니저와 트랜잭션도 기본적으로 1:1이지만 실제 동작은 다릅니다. **`SimpleJpaRepository`처럼 싱글톤 빈에 주입된 `EntityManager`는 실제 객체가 아니라 Spring이 `SharedEntityManagerCreator`로 만든 프록시입니다.** 이 프록시가 메서드 호출 시점에 현재 스레드에 바인딩된 실제 `EntityManager`를 찾아 위임합니다. 싱글톤이지만 각 요청(트랜잭션)마다 다른 `EntityManager`가 동작하는 이유입니다.

확장된 영속성 컨텍스트(OSIV 등)에서는 여러 트랜잭션에 걸쳐 하나의 영속성 컨텍스트가 유지될 수도 있습니다.

### SQL JOIN과 JPQL JOIN의 기준 차이

SQL은 **테이블 간 물리적 컬럼**을 기준으로 조인합니다. ON 절에 조인 조건을 직접 명시해야 합니다.

```sql
-- SQL: 조인 조건을 직접 작성
SELECT * FROM reservation r
JOIN screening s ON r.screening_id = s.screening_id
```

JPQL은 **객체의 연관관계 필드**를 기준으로 조인합니다. FK 설정이 이미 엔티티 매핑에 선언되어 있으므로 ON 절 없이 필드명만 씁니다.

```java
// JPQL: 연관관계 필드명으로 조인
SELECT r FROM Reservation r JOIN r.screening s
```

`r.screening`은 `Reservation` 엔티티의 연관관계 필드입니다. Hibernate가 이 매핑 정보를 읽어 적절한 SQL ON 절을 자동 생성합니다. `JOIN FETCH`는 JPQL에만 있는 개념으로, SQL로는 평범한 INNER JOIN으로 번역되지만 JPA 차원에서 연관 엔티티를 즉시 초기화합니다.

### 프록시 (Proxy)

**프록시란 무엇인가요**

`@ManyToOne(fetch = LAZY)` 설정 시 연관 엔티티를 즉시 조회하지 않고 실제 데이터가 필요한 순간까지 미룹니다. 이때 JPA는 실제 엔티티 대신 **프록시 객체**를 반환합니다. 프록시는 실제 엔티티 클래스를 상속해 Hibernate가 런타임에 생성한 가짜 객체로, 처음에는 id만 들고 있다가 다른 필드에 접근하는 순간 DB에서 실제 데이터를 조회(초기화)합니다.

**프록시와 N+1 문제의 관계**

LAZY 로딩으로 프록시를 받은 후 루프에서 각 프록시를 초기화하면 N번의 추가 SELECT가 발생합니다.

```java
List<Reservation> list = reservationRepository.findAll();  // SELECT 1번
for (Reservation r : list) {
    r.getScreening().getStartAt();  // 프록시 초기화 → SELECT N번
}
```

fetch join으로 연관 엔티티를 미리 함께 로딩하거나, `@BatchSize`로 IN 절 배치 조회하는 것이 해결책입니다.

**Hibernate Proxy vs Spring AOP Proxy**

| | Hibernate Proxy | Spring AOP Proxy |
|---|---|---|
| 생성 방식 | CGLIB로 엔티티 클래스를 상속 | CGLIB 상속 또는 JDK 동적 프록시(인터페이스) |
| 목적 | LAZY 로딩 (DB 조회 지연) | AOP 적용 (트랜잭션, 로깅 등) |
| 생성 시점 | 연관 엔티티 조회 시 | 빈 등록 시 |
| 클래스명 예시 | `Screening$HibernateProxyXXX` | `ReservationService$$SpringCGLIB$$0` |

둘 다 CGLIB 바이트코드 조작 기술을 활용하지만 목적과 생성 주체가 다릅니다.

### CGLIB
1주차 발표 때 질문 받았던 사항이고 제대로 알아보지 않아 추가 정리해보았습니다.

CGLIB(Code Generation Library)는 런타임에 바이트코드를 조작해 **클래스의 서브클래스를 동적으로 생성**하는 라이브러리입니다. Spring은 `@Transactional` 같은 AOP 기능을 적용할 때 대상 클래스를 상속한 CGLIB 프록시 클래스를 만들어 빈으로 등록합니다.

컨트롤러에서 `@RequiredArgsConstructor`로 주입받는 서비스 객체는 실제 `ReservationService`가 아니라 CGLIB가 만든 프록시입니다.

```java
// ReservationController
private final ReservationService reservationService;

// 실제 타입 확인
System.out.println(reservationService.getClass().getName());
// → com.ceos24.cgv.service.ReservationService$$SpringCGLIB$$0
```

프록시는 `ReservationService`를 상속했으므로 외부에서는 일반 객체와 구분이 되지 않습니다. 메서드가 호출되면 프록시가 트랜잭션 시작/종료 같은 부가 로직을 끼워 넣은 뒤 원본 메서드로 위임합니다.

CGLIB 프록시가 생성되려면 클래스에 기본 생성자가 필요하고 메서드가 `final`이면 안 됩니다. `final` 메서드는 오버라이드할 수 없어 프록시가 끼어들지 못합니다.

### SimpleJpaRepository의 EntityManager 주입이 동작하는 방식

`SimpleJpaRepository`는 싱글톤 빈입니다. 그런데 생성자 주입으로 `EntityManager`를 받습니다. `EntityManager`는 요청(트랜잭션)마다 생성되는 객체인데, 싱글톤에 한 번만 주입하면 어떻게 요청마다 다른 세션이 동작할까요?

답은 **주입되는 `EntityManager` 자체가 프록시**이기 때문입니다. Spring은 `SharedEntityManagerCreator`가 만든 프록시 `EntityManager`를 주입합니다.

```java
// SimpleJpaRepository (Spring Data JPA 내부)
public SimpleJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
    this.em = entityManager;  // 실제로는 프록시가 주입됨
}
```

`this.em.find(...)` 같이 호출하면 프록시가 현재 스레드에 바인딩된 트랜잭션 컨텍스트를 조회하고, 거기서 실제 `EntityManager`를 꺼내 위임합니다. 싱글톤이지만 각 요청이 자신의 영속성 컨텍스트를 갖게 되는 이유입니다.

### fetch join 사용 시 알아두어야 할 것들

**컬렉션 fetch join과 DISTINCT**

1:N 관계를 fetch join하면 SQL 결과에서 1 쪽 행이 N 개만큼 중복됩니다. `Reservation` 1건에 `ReservationSeat` 3개가 있으면 SQL 결과는 `Reservation` 행 3개입니다.

```java
SELECT r FROM Reservation r JOIN FETCH r.seats
```

그렇다고 Java 목록에 `Reservation`이 3번 들어오지는 않습니다. Hibernate 6(Spring Boot 3.x)부터 **엔티티 목록의 중복 제거는 항상 자동**이고, 이를 끄던 `hibernate.query.passDistinctThrough` 옵션 자체가 없어졌습니다.

반면 JPQL에 쓴 `DISTINCT`는 SQL로 그대로 전달됩니다. 중복 제거 효과는 달라지지 않는데 조인 결과 전체에 `DISTINCT`를 거는 비용만 남습니다. 그래서 이 프로젝트의 fetch join 쿼리에는 `DISTINCT`를 쓰지 않습니다. 중복 제거가 목적이 아니라 DB에 실제로 `DISTINCT`가 필요한 집계라면 그때는 의미가 있습니다.

**HHH000104 — fetch join과 페이징을 동시에 쓰면 안 됩니다**

```
HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
```

컬렉션 fetch join + 페이징(`setFirstResult` / `setMaxResults`)을 동시에 사용하면 DB에서 페이징을 적용할 수 없습니다. Hibernate는 전체 데이터를 메모리에 올린 후 Java에서 페이징을 처리하며, 데이터가 많을수록 OOM 위험이 커집니다.

해결책은 ToOne 관계만 fetch join하고 컬렉션은 `@BatchSize` 또는 `hibernate.default_batch_fetch_size`로 IN 절 배치 로딩하거나, 부모 페이징 후 자식을 별도 쿼리로 분리하는 것입니다.

**fetch join에서 발생하는 3가지 에러**

*(1) HHH000104* — 위의 페이징 문제와 동일합니다. 예외가 아닌 경고지만 운영 환경에서 치명적입니다.

*(2) owner of the fetched association was not present in the select list*

```
query specified join fetching, but the owner of the fetched association was not present in the select list
```

fetch join한 연관 엔티티의 소유자(부모)가 SELECT 절에 없을 때 발생합니다.

```java
// 잘못된 예: Screening은 select하지 않고 theater를 fetch join
SELECT s.movie FROM Screening s JOIN FETCH s.theater
```

fetch join의 소유자(`Screening`)가 SELECT에 없으므로 Hibernate가 `theater`를 어디에 붙여야 할지 알 수 없습니다. SELECT 절에 루트 엔티티를 포함해야 합니다.

*(3) MultipleBagFetchException*

```
org.hibernate.loader.MultipleBagFetchException: cannot simultaneously fetch multiple bags
```

`List` 타입 컬렉션 2개 이상을 동시에 fetch join하면 카테시안 곱이 발생합니다. Hibernate는 이를 막기 위해 예외를 던집니다.

```java
// 예외 발생: List 컬렉션 2개를 동시에 fetch join
SELECT r FROM Reservation r
JOIN FETCH r.seats
JOIN FETCH r.someOtherList
```

해결책은 컬렉션 중 하나를 `Set`으로 변경하거나, fetch join을 하나만 유지하고 나머지는 `@BatchSize`로 지연 로딩하는 것입니다.

---

## 배운점 및 느낀점

### ORM과 JPA — 엔티티가 상태를 스스로 지킨다

JPA를 쓰면 객체를 DB 행처럼 다루고 싶어지는 유혹이 생깁니다. `@Setter`를 열어두고 서비스에서 필드를 직접 건드리는 방식입니다. 이렇게 하면 "어떤 이유로 이 필드가 바뀌었는지"를 코드에서 추적할 수 없게 됩니다.

대신 엔티티에 의도가 드러나는 메서드를 두고, 상태 변경은 전부 그 메서드를 통하게 했습니다. 생성자는 `private` + `@Builder`, 기본 생성자는 `@NoArgsConstructor(PROTECTED)`로 외부에서 빈 객체가 만들어지지 않도록 막았습니다.

```java
// Reservation.java
public void cancel(LocalDateTime now) {
    if (this.status == ReservationStatus.CANCELLED) {
        throw new CustomException(ErrorCode.ALREADY_CANCELLED);
    }
    if (this.status == ReservationStatus.EXPIRED) {
        throw new CustomException(ErrorCode.RESERVATION_EXPIRED);
    }
    if (this.status == ReservationStatus.RESERVED && !isCancellableAt(now)) {
        throw new CustomException(ErrorCode.CANCEL_DEADLINE_PASSED);
    }
    this.status = ReservationStatus.CANCELLED;
    this.cancelledAt = now;
    releaseSeats();
}
```

취소 가능 여부 판단부터 좌석 해제까지 엔티티 안에서 끝납니다. 서비스는 소유자 조건으로 예매를 찾아 `cancel()`을 부를 뿐입니다. 좌석 행은 지우지 않고 `release_key`만 바꿔 어느 좌석을 얼마에 잡았는지 남깁니다(「중복 예매 방지」 참고). 현재 시각은 `Clock` 빈에서 받아 인자로 넘기므로 "상영 20분 전" 같은 경계를 테스트에서 만들 수 있습니다. 수정한 엔티티를 따로 `save()`하지 않아도 트랜잭션 커밋 시 dirty checking이 부모와 자식 행의 변경을 감지해 UPDATE를 실행합니다.

### 로딩 전략과 N+1 문제 — fetch join으로 조회 형태에 맞춘다

모든 `@ManyToOne`을 `LAZY`로 설정했습니다. 기본값인 `EAGER`는 연관 엔티티를 항상 끌어오므로, 필요하지 않은 조인이 늘 실행됩니다. `LAZY`는 실제로 접근하는 순간 SELECT를 날리는데, 루프 안에서 접근하면 행 수만큼 쿼리가 나가는 N+1 문제가 생깁니다.

해결책은 조회 API의 응답 형태를 먼저 정하고, 필요한 연관만 fetch join으로 한 번에 가져오는 것입니다.

```java
// ReservationRepository.java
@Query("""
        SELECT r FROM Reservation r
        JOIN FETCH r.screening s
        JOIN FETCH s.movie
        JOIN FETCH s.theater t
        JOIN FETCH t.branch
        LEFT JOIN FETCH r.seats
        WHERE r.id = :id AND r.user.id = :userId
        """)
Optional<Reservation> findOwnedWithDetails(@Param("id") Long id, @Param("userId") Long userId);
```

`r.user.id` 조건은 소유권 검사입니다. 남의 예매는 아예 로딩되지 않습니다. `user`를 조인하지 않은 것은 의도입니다. 응답이 사용자를 id로만 쓰는데, 프록시의 id getter는 초기화 없이 식별자를 돌려주므로 조인해도 쿼리가 줄지 않습니다(`ReservationQueryCountTest`로 실측했습니다). 이름 같은 다른 필드를 응답에 실으면 그때 fetch join을 더해야 합니다. 컬렉션 fetch join이 2개 이상이면 `MultipleBagFetchException`이 발생하므로, 그 경우에는 각각 별도 쿼리로 조회한 뒤 조립해야 합니다. 회차 목록의 잔여좌석 카운트는 IN 절 + GROUP BY로 한 번에 집계해(`countGroupedByScreeningIds`) N+1을 방지했습니다.

### REST API — 자원 URL + HTTP 메서드 + 상태 코드

REST는 URL이 자원을 가리키고, 행위는 HTTP 메서드로 표현하는 설계입니다. `/reservations/cancel/{id}` 같은 동사 URL 대신 `/reservations/{id}`에 DELETE를 보내는 방식입니다.

```java
// ReservationController.java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public ApiResponse<ReservationResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                               @Valid @RequestBody ReservationCreateRequest req) {
    return ApiResponse.success(reservationService.create(authUser.userId(), req));
}

@DeleteMapping("/{id}")
public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthUser authUser,
                                @PathVariable Long id) {
    reservationService.cancel(id, authUser.userId());
    return ApiResponse.success();
}
```

`ResponseEntity`로 감싸지 않고 공통 응답 `ApiResponse`로 DTO를 감싸 반환하며, 상태 코드는 `@ResponseStatus`로 표현했습니다. 예매자는 요청 본문이 아니라 토큰에서 꺼낸 `AuthUser`로 정하고, 서비스에는 `userId`만 넘겨 서비스가 인증 방식을 모르게 했습니다. 요청 검증은 `@Valid` + record 필드의 `@NotNull`/`@Min`으로 컨트롤러 진입 직후에 끝냅니다. 검증이 실패하면 `MethodArgumentNotValidException`이 발생하고 전역 핸들러가 400으로 처리합니다.

### 예외 처리 — 도메인 예외를 하나로 모아 응답 형식을 통일

서비스와 도메인은 `CustomException(ErrorCode)`만 던지고, `@RestControllerAdvice`인 `GlobalExceptionHandler`가 HTTP 응답으로 변환합니다. `IllegalStateException` 같은 표준 예외를 직접 던지면 응답 형식을 통제할 수 없습니다.

```java
// GlobalExceptionHandler.java
@ExceptionHandler(CustomException.class)
public ResponseEntity<ApiResponse<Void>> handleCustom(CustomException e) {
    ErrorCode code = e.getErrorCode();
    log.warn("[CustomException] {}: {}", code.name(), e.getMessage());
    return ResponseEntity.status(code.getHttpStatus())
                         .body(ApiResponse.error(code));
}
```

`ErrorCode` enum이 `HttpStatus`와 메시지를 함께 들고 있습니다. 새 오류를 추가할 때 enum에 한 줄만 추가하면 핸들러 코드는 변경하지 않아도 됩니다. 핸들러는 도메인 오류, 요청 검증·바인딩 오류(400), 예상치 못한 오류(500)로 나누고 로그 레벨(`warn` vs `error`)도 구분합니다. 단, 인증·인가 실패는 `DispatcherServlet`에 닿기 전 필터 단계에서 일어나 이 핸들러가 잡지 못합니다. 그래서 `SecurityErrorResponder`가 같은 `ApiResponse` 형식으로 응답을 직접 씁니다.

### Spring MVC 흐름 — DispatcherServlet → Controller → Service → Repository

요청은 `DispatcherServlet`이 받아서 URL과 HTTP 메서드로 핸들러를 고르고, `@RequestMapping`이 달린 컨트롤러 메서드로 전달합니다. 컨트롤러는 HTTP 관심사(검증, DTO 매핑)만 담당하고 비즈니스 로직은 서비스로 위임합니다.

3주차에 Spring Security를 도입하면서 `DispatcherServlet` 앞에 필터 체인이 생겼습니다. `JwtAuthenticationFilter`가 토큰을 검증해 `SecurityContext`에 사용자를 넣고, `AuthorizationFilter`가 경로 규칙으로 요청을 거부하거나 통과시킵니다. 컨트롤러는 `@AuthenticationPrincipal`로 인증된 사용자를 받습니다. 자세한 순서는 「인증·인가」에 정리했습니다. 인터셉터는 아직 쓰지 않습니다.

### 레이어드 아키텍처 — 의존 방향을 한쪽으로

Controller → Service → Repository 단방향 의존입니다. 상위 계층은 하위를 알지만 하위는 상위를 모릅니다. 이 원칙에서 세 가지 규칙이 파생됩니다.

첫째, 컨트롤러가 엔티티를 보면 안 됩니다. 서비스는 항상 DTO를 반환합니다.

```java
// ReservationService.java
public ReservationResponse getById(Long id, Long userId) {
    List<ReservationDetailRow> rows = reservationRepository.findOwnedDetailRows(id, userId);
    if (rows.isEmpty()) {
        throw new CustomException(ErrorCode.RESERVATION_NOT_FOUND);
    }
    return ReservationResponse.of(rows, LocalDateTime.now(clock));
}
```

조회는 상태를 바꾸지 않으므로 엔티티 대신 응답에 필요한 컬럼만 담은 프로젝션(`ReservationDetailRow`)으로 읽습니다.

둘째, 엔티티·프로젝션→DTO 변환은 DTO의 정적 팩토리(`ReservationResponse.from`, `of`)가 소유합니다. 서비스는 조립 순서만 정하고 매핑 규칙은 DTO 파일 안에 있어, 응답 형태가 바뀌어도 서비스 코드는 변하지 않습니다.

셋째, 트랜잭션 경계는 서비스 계층이 책임집니다. 클래스 레벨에 `@Transactional(readOnly = true)`를 두고 쓰기 메서드에만 `@Transactional`을 추가해 기본값을 오버라이드합니다. 읽기와 쓰기의 트랜잭션을 명시적으로 분리함으로써 불필요한 flush와 dirty checking 비용을 막습니다.

### 서비스 단위 테스트 — Mock으로 비즈니스 로직만 검증한다

이전 프로젝트에서는 테스트 코드를 거의 작성해본 적이 없었습니다. 이번에 처음으로 단위 테스트를 제대로 작성해보면서, 테스트가 단순히 "동작 확인용"이 아니라 코드 설계를 검증하는 수단이라는 걸 느꼈습니다.

서비스 단위 테스트는 `@ExtendWith(MockitoExtension.class)`와 `@Mock` / `@InjectMocks`를 조합해 외부 의존성(DB, 다른 서비스)을 전부 Mock으로 대체합니다. 덕분에 순수하게 비즈니스 로직만 빠르게 검증할 수 있습니다.

```java
// ReservationServiceTest.java
@Test
void 없는_회차면_SCREENING_NOT_FOUND() {
    given(screeningRepository.findByIdWithDetails(1L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(1L, reqOf(1L, new int[]{1, 1})))
            .isInstanceOf(CustomException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.SCREENING_NOT_FOUND);
    verify(userRepository, never()).findById(any());
}
```

`BDDMockito.given().willReturn()`으로 의존 객체의 동작을 사전에 정의하고, `assertThatThrownBy().extracting()`으로 예외 타입과 에러코드를 한 번에 검증합니다. `verify(userRepository, never())`는 회차 조회가 실패하면 회원 조회 자체가 호출되지 않아야 한다는 것을 보장합니다. 성공 케이스뿐 아니라 이런 "호출되면 안 된다"는 보장도 테스트로 표현할 수 있다는 점이 인상적이었습니다.

저장된 객체의 내부 상태를 검증할 때는 `ArgumentCaptor`를 활용합니다. `reservationRepository.saveAndFlush()`에 넘겨진 `Reservation` 객체를 직접 꺼내서 좌석 수, 각 좌석의 `paidPrice` 등을 확인할 수 있습니다.

`@Setter` 없이 `private` 생성자만 허용하는 엔티티에 테스트용 id를 넣어야 할 때는 `ReflectionTestUtils.setField()`를 사용했습니다. 운영 코드의 불변 설계를 깨지 않으면서 테스트에서만 필드를 강제 주입하는 방법입니다.

### 컨트롤러 통합 테스트 — 실제 HTTP 요청처럼 end-to-end 검증한다

서비스 단위 테스트가 비즈니스 로직을 검증한다면, 컨트롤러 통합 테스트는 요청이 들어와서 응답이 나가기까지의 전체 흐름을 검증합니다. 공통 설정은 추상 베이스 클래스에 모아두고 각 테스트 클래스가 상속합니다.

```java
// ControllerIntegrationTest.java
@SpringBootTest
@Transactional
public abstract class ControllerIntegrationTest {

    protected MockMvc mockMvc;

    @Autowired protected EntityManager em;
    @Autowired private WebApplicationContext wac;
    @Autowired private JwtProvider jwtProvider;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    protected <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    protected void flushAndClear() {
        em.flush();
        em.clear();
    }

    protected RequestPostProcessor bearer(User user) {
        return request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION,
                    "Bearer " + jwtProvider.createAccessToken(user.getId(), user.getRole()));
            return request;
        };
    }
}
```

`apply(springSecurity())`가 있어야 MockMvc 요청이 Security 필터 체인을 탑니다. 빠지면 토큰을 보내도 필터가 풀지 않아, 보호 API가 401이 아니라 principal이 null인 채 컨트롤러까지 가서 500이 됩니다. `bearer(user)`는 요청을 보내는 순간 실제 `JwtProvider`로 토큰을 발급해 헤더에 넣습니다.

`@Transactional`을 베이스 클래스에 붙여두면 테스트가 끝난 뒤 자동으로 롤백됩니다. 각 테스트가 서로의 데이터를 오염시키지 않으므로 독립성을 보장합니다.

`flushAndClear()`는 `em.flush() + em.clear()`의 조합입니다. flush로 변경 사항을 DB에 반영한 뒤 clear로 1차 캐시를 비우면, 이후 조회가 캐시 대신 실제 DB에서 읽어오므로 "실제로 저장됐는지"를 검증할 수 있습니다. 특히 좌석 현황 조회처럼 DB에서 집계한 결과를 확인할 때 반드시 필요했습니다.

실제 테스트 메서드는 `MockMvc`로 HTTP 요청을 보내고 `jsonPath`로 응답 JSON을 검증합니다.

```java
// ReservationControllerTest.java
@Test
void 좌석_선점_성공() throws Exception {
    mockMvc.perform(post("/api/reservations").with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"screeningId":%d,"seats":[
                              {"rowNum":1,"colNum":1,"audienceType":"ADULT"},
                              {"rowNum":1,"colNum":2,"audienceType":"YOUTH"}]}
                            """.formatted(screening.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.userId").value(user.getId()))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.seats.length()").value(2))
            .andExpect(jsonPath("$.data.totalPrice").value(25200));
}
```

테스트 데이터 생성은 `TestFixtures` 클래스에 정적 팩토리 메서드로 모아두었습니다. 테스트마다 Builder를 반복 작성하지 않고 `TestFixtures.branch("강남점")` 한 줄로 의미 있는 이름의 픽스처를 만들 수 있어 가독성이 높아졌습니다.

---

## JWT 인증 흐름 정리

3주차 과제 1번 질문에 대한 정리입니다. 각 항목 끝에 이 프로젝트의 실제 코드 위치를 적었습니다. 경로는 `src/main/java/com/ceos24/cgv/` 아래 기준입니다.

### 1. JWT의 헤더, 페이로드, 서명은 각각 어떤 역할을 하나요?

**핵심** 헤더는 토큰을 어떻게 검증할지, 페이로드는 토큰이 무엇을 주장하는지 담고, 서명은 그 둘이 바뀌지 않았음을 증명합니다.

**설명**

| 구성요소 | 역할 | 들어가는 값 |
|---|---|---|
| 헤더 | 토큰을 어떻게 검증해야 하는지 알려주는 정보 | 서명 알고리즘(`alg`), 토큰 종류(`typ`), 키가 여러 개면 서명한 키(`kid`) |
| 페이로드 | 토큰이 주장하는 내용, 즉 클레임 | 누구의 토큰인지(`sub`), 언제 만료되는지(`exp`), 누가 발급했는지(`iss`) 등 |
| 서명 | 헤더와 페이로드가 바뀌지 않았다는 증명 | 인코딩된 헤더와 페이로드를 점으로 이은 문자열을 비밀키로 서명한 값 |

서버는 받은 토큰의 앞 두 부분으로 서명을 다시 계산해 비교하고, 한 글자라도 바뀌었으면 불일치로 변조를 잡아냅니다.

헤더와 페이로드는 암호화가 아니라 `Base64URL` 인코딩일 뿐이라 누구나 디코딩해서 읽을 수 있습니다. 서명은 "내용이 바뀌지 않았고 우리 서버가 발급했다"는 무결성만 보장하고 내용을 숨겨주지 않습니다. 그래서 비밀번호나 개인정보를 넣으면 안 됩니다. 내용까지 숨기려면 `JWE`라는 별도 규격을 써야 합니다.

**우리 프로젝트에서는**

`JwtProvider.createAccessToken()`이 발급한 토큰의 앞 두 부분을 디코딩한 예시입니다(테스트 설정으로 발급, 서명 부분 생략).

```
header   {"alg":"HS256"}
payload  {"sub":"1","role":"USER","iss":"cgv-api","iat":1790480152,"exp":1790481952}
```

- `typ`는 선택값이라 JJWT가 넣지 않습니다. 헤더에는 `alg`만 있습니다.
- 헤더의 `alg`는 토큰을 만든 쪽이 정하는 값이라, 파서가 허용할 알고리즘을 `sig().clear().add(Jwts.SIG.HS256)`로 HS256 하나로 못박았습니다. `alg: none`이나 HS512로 서명한 토큰은 `TOKEN_INVALID`입니다.
- 서명이 맞지 않는 토큰(payload 변조, 다른 키로 서명)은 `TOKEN_INVALID`입니다. `AuthenticationScenarioTest`의 변조·다른 키 테스트가 확인합니다.
- 코드: `global/security/jwt/JwtProvider.java`

**참고** [JJWT 공식 문서](https://github.com/jwtk/jjwt) — "What is a JSON Web Token?" 절 · [RFC 8725 3절](https://www.rfc-editor.org/rfc/rfc8725#section-3)

### 2. 액세스 토큰과 리프레시 토큰은 무엇이 다른가요?

**핵심** 액세스 토큰은 API를 호출할 때마다 보내는 수명이 짧은 출입증이고, 리프레시 토큰은 액세스 토큰이 만료됐을 때 새로 받기 위한 수명이 긴 재발급권입니다.

**설명**

| | 액세스 토큰 | 리프레시 토큰 |
|---|---|---|
| 용도 | API 호출 시 인증 | 액세스 토큰 재발급 |
| 수명 | 짧게 (수십 분 ~ 한 시간) | 길게 (수일 ~ 수주) |
| 보내는 곳 | 모든 보호 API | 재발급 경로만 |
| 탈취 시 피해 | 금방 만료되어 제한적 | 크다. 계속 새 액세스 토큰을 받을 수 있음 |
| 저장 위치 (서버 쪽) | 저장하지 않고 서명으로 검증 | 서버에 저장해 두고 폐기할 수 있게 관리 |

액세스 토큰은 매 요청마다 네트워크를 타서 노출 위험이 크므로 수명을 짧게 잡아 탈취되더라도 금방 만료되게 합니다. 리프레시 토큰은 노출 빈도는 낮지만 탈취되면 피해가 크므로 서버에서 폐기할 수 있게 관리하는 것이 보통입니다.

RFC 6749 기준으로 두 토큰 모두 JWT일 필요는 없습니다. 특히 리프레시 토큰은 서버 저장소 조회로 검증하는 경우가 많아 의미 없는 무작위 문자열로 만드는 것도 흔합니다.

**우리 프로젝트에서는**

- 두 토큰을 모두 구현했습니다. 액세스 토큰은 JWT, 리프레시 토큰은 JWT가 아닌 256비트 무작위 문자열이고 DB에 해시로 저장합니다. 자세한 내용은 아래 「리프레시 토큰」에 있습니다.
- 유효기간은 각각 `JWT_ACCESS_TOKEN_VALIDITY`(예: `30m`)와 `REFRESH_TOKEN_VALIDITY`(예: `14d`)로 정하고, 로그인 응답의 `expiresIn`, `refreshTokenExpiresIn`(초)으로 알립니다.
- 액세스 토큰이 만료되면 `POST /api/auth/reissue`로 재발급받습니다. 권한은 재발급 시점의 DB 값으로 다시 정해집니다.
- 이미 발급한 액세스 토큰은 여전히 만료 전에 무효화할 수 없습니다. 로그아웃해도 마찬가지이며, 짧은 유효기간이 완화 수단입니다.
- 재발급할 때마다 리프레시 토큰도 새로 발급하고(순환 발급), 이미 쓴 리프레시 토큰이 다시 오면 그 로그인의 토큰을 모두 폐기합니다(재사용 탐지). 리프레시 토큰의 수명은 로그인 시점 기준으로 고정이라 재발급해도 늘어나지 않습니다.
- 코드: `global/security/jwt/JwtProperties.java`, `global/security/refresh/RefreshTokenProvider.java`, `domain/user/dto/TokenResponse.java`

**참고** [RFC 6749 1.4절](https://www.rfc-editor.org/rfc/rfc6749#section-1.4) · [RFC 6749 1.5절](https://www.rfc-editor.org/rfc/rfc6749#section-1.5)

### 3. 쿠키와 세션, JWT는 각각 어떤 역할을 하나요?

**핵심** 쿠키는 브라우저의 저장·전달 수단, 세션은 서버 쪽 상태 저장 방식, JWT는 토큰 형식입니다.

**설명**

| | 정체 | 상태를 누가 들고 있나 | 전달 방식 |
|---|---|---|---|
| 쿠키 | 브라우저의 저장·전달 수단 | 브라우저 | 서버가 `Set-Cookie`로 내려주면 브라우저가 저장하고, 같은 도메인으로 요청할 때마다 자동으로 붙여 보냄 |
| 세션 | 서버 쪽 상태 저장 방식 | 서버 저장소 (클라이언트는 세션 ID만 가짐) | 세션 ID를 보통 쿠키로 전달 |
| JWT | 토큰 형식 | 토큰 자체 | 쿠키에 담을 수도, 헤더에 담을 수도 있음 |

셋은 같은 기준으로 비교할 대상이 아닙니다. 쿠키는 전달 수단이고, 세션은 상태를 두는 방식이며, JWT는 형식입니다. 그래서 "세션 방식과 JWT 방식"의 진짜 차이는 상태를 서버가 들고 있느냐, 토큰이 들고 있느냐입니다. 세션은 서버가 강제 로그아웃시키기 쉽지만 서버가 여러 대면 세션 공유가 필요합니다. JWT는 서버 확장이 쉽지만 이미 발급한 토큰을 만료 전에 무효화하기 어렵습니다.

**액세스 토큰은 반드시 JWT여야 하나요?** 아닙니다. 액세스 토큰은 "이 요청을 허용해도 된다"는 증표일 뿐이고, 형식은 정해져 있지 않습니다. 형식은 크게 두 가지입니다.

| | 자체 포함 토큰 (JWT) | 불투명 토큰 (무작위 문자열) |
|---|---|---|
| 담긴 정보 | 사용자 id, 권한, 만료 시각을 토큰이 직접 가짐 | 아무 의미 없는 값. 정보는 서버 저장소에 있음 |
| 검증 방법 | 서명과 만료만 확인. 저장소 조회 없음 | 요청마다 저장소 조회 |
| 즉시 무효화 | 어려움. 만료될 때까지 유효 | 저장소에서 표시하면 바로 무효 |

JWT를 쓰면 요청마다 저장소를 조회하지 않아도 되지만, 발급한 토큰을 즉시 무효화할 수는 없습니다.

**JWT는 반드시 `Authorization` 헤더로 보내야 하나요?** 아닙니다. JWT는 형식일 뿐이라 쿠키에 담아 보내도 됩니다. 다만 어디에 담느냐에 따라 막아야 할 공격이 달라집니다.

| 담는 곳 | 전송 방식 | 막아야 할 공격 |
|---|---|---|
| 쿠키 | 브라우저가 자동으로 붙여 보냄 | CSRF. 다른 사이트에서 보낸 요청에도 쿠키가 실리므로 `SameSite`나 CSRF 토큰이 필요합니다. `HttpOnly`로 두면 스크립트가 토큰을 읽지 못해, XSS가 생겨도 토큰이 밖으로 빠져나가지는 않습니다 |
| `Authorization` 헤더 | 클라이언트 코드가 직접 넣어야 전송됨 | XSS. CSRF에는 안전하지만, 클라이언트가 스크립트로 읽을 수 있는 곳에 토큰을 보관하므로 XSS가 생기면 탈취될 수 있습니다 |

**우리 프로젝트에서는**

- 액세스 토큰은 JWT이고, 리프레시 토큰은 불투명 토큰입니다. 액세스 토큰은 모든 보호 API에서 검증하므로 DB를 조회하지 않는 JWT로 만들었습니다. 리프레시 토큰은 재발급과 로그아웃에서만 쓰고 폐기 여부를 어차피 DB에서 확인해야 하므로, 토큰 안에 정보를 담을 이유가 없습니다. 그래서 256비트 무작위 문자열로 발급합니다(`RefreshTokenProvider.generate()`).
- JWT를 `Authorization: Bearer` 헤더로만 받고 세션과 인증 쿠키는 쓰지 않습니다. `JwtAuthenticationFilter.resolveToken()`이 헤더에서만 토큰을 찾습니다.
- `SessionCreationPolicy.STATELESS`로 Security가 세션을 만들지 않게 했고, `formLogin`, `httpBasic`, `logout`도 껐습니다. 응답에 `Set-Cookie`가 없고 세션이 생기지 않는 것을 `SecurityConfigTest`가 확인합니다.
- 헤더 전달이라 CSRF 보호를 껐습니다. 이유는 아래 「인증·인가 — CSRF 비활성화」에 있습니다.
- 코드: `global/config/SecurityConfig.java`, `global/security/jwt/JwtAuthenticationFilter.java`

**참고** [Spring Security 공식 문서 — Persisting Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)

### 4. CGV 프로젝트의 액세스 토큰에는 어떤 클레임이 필요한가요?

**핵심** 누구인지(`sub`), 무엇을 할 수 있는지(`role`), 언제까지 유효한지(`iat`, `exp`), 누가 발급했는지(`iss`)만 넣고 개인정보는 넣지 않습니다.

**설명**

| 클레임 | 값 | 넣은 이유 |
|---|---|---|
| `sub` | 사용자 ID (문자열) | 누구의 토큰인지 식별. 이메일은 바뀔 수 있고 개인정보라 ID를 씁니다 |
| `role` | `USER` 또는 `ADMIN` | 관리자 API 인가에 필요. 표준 클레임이 아닌 자체 클레임 |
| `iat` | 발급 시각 | 발급 시점 추적 |
| `exp` | 만료 시각 | 발급 시 항상 넣습니다. 없으면 영원히 유효한 토큰이 됩니다 |
| `iss` | 고정 발급자 문자열 | 우리 서버가 발급한 토큰인지 추가로 확인 |

| 넣지 않은 정보 | 넣지 않은 이유 |
|---|---|
| 비밀번호, 이메일, 전화번호, 이름, 생년월일 | 페이로드는 누구나 디코딩해서 읽을 수 있습니다 |
| 영화 ID, 예매 ID | 요청할 때마다 대상이 달라지므로 URL 경로나 본문으로 받습니다. 토큰은 로그인 때 한 번 발급해 여러 요청에 쓰기 때문에, 이런 ID를 넣으면 찜하거나 예매할 때마다 토큰을 새로 발급해야 합니다. 예매를 취소해도 이미 발급된 토큰에는 옛 값이 남습니다. 내 예매인지는 토큰의 `sub`와 DB에 저장된 예매 소유자를 비교해 확인합니다 |
| `aud` | 아래 문단 참고 |

`aud`는 토큰을 받아야 할 대상을 적는 클레임입니다. 한 발급자가 여러 서비스용 토큰을 발급할 때, 어떤 서비스용으로 받은 토큰을 다른 서비스에 들고 가서 쓰는 것을 막기 위해 씁니다. 이 프로젝트는 발급하는 서버와 토큰을 받는 서버가 같고 받는 서비스가 하나뿐이라, 토큰을 다른 곳에 들고 갈 대상이 없으므로 `aud`를 생략합니다.

단, 이 판단은 서명키가 이 서비스 전용이라는 전제에서만 성립합니다. 같은 키를 다른 서비스나 다른 환경과 함께 쓰면 그쪽에서 발급된 토큰이 여기서도 통과됩니다. 이를 막기 위해 서명키는 외부 설정으로 분리해 서비스와 환경마다 따로 둡니다.

**우리 프로젝트에서는**

- `iss`는 코드 상수 `cgv-api`이고, 파서가 `requireIssuer()`로 다른 발급자를 `TOKEN_INVALID`로 거부합니다.
- `role`에는 접두사 없는 `Role.name()`을 싣습니다. Spring Security용 `ROLE_` 접두사는 `Role.getAuthority()` 한 곳에서만 붙입니다.
- `exp`는 발급 시 항상 넣지만, 검증 단계에서 `exp`의 존재를 강제하지는 않습니다. JJWT는 `exp`가 있을 때만 만료를 검사합니다. 현재는 서명키를 가진 쪽이 이 서버뿐이라 `exp` 없는 토큰이 만들어질 경로가 없습니다.
- `sub`나 `role`을 읽을 수 없으면 `JwtProvider.toAuthUser()`가 `TOKEN_INVALID`로 처리합니다.
- 서명키는 환경변수 `JWT_SECRET`으로만 주입합니다.
- 코드: `global/security/jwt/JwtProvider.java`, `domain/user/entity/Role.java`

**참고** [RFC 7519 4.1절](https://www.rfc-editor.org/rfc/rfc7519#section-4.1) · [RFC 8725 3절](https://www.rfc-editor.org/rfc/rfc8725#section-3)

### 5. JWT 검증 결과가 Authentication과 SecurityContext로 어떻게 연결되나요?

**핵심** 필터가 검증한 토큰으로 인증 완료 상태의 `Authentication`을 만들어 `SecurityContext`에 담으면, 그 요청 동안 인가와 컨트롤러가 이를 꺼내 씁니다.

**설명**

```mermaid
flowchart TD
    A["요청 (Authorization: Bearer 토큰)"] --> B["JwtAuthenticationFilter.resolveToken()"]
    B --> C["JwtProvider.parse() — 서명·만료·발급자 검증"]
    C -->|성공| D["AuthUser(userId, role) 생성"]
    C -->|실패| X["실패 원인을 request attribute에 기록, 익명으로 통과"]
    D --> E["UsernamePasswordAuthenticationToken.authenticated()"]
    E --> F["새 SecurityContext에 담아 SecurityContextHolder.setContext()"]
    F --> G["AuthorizationFilter — 경로 규칙으로 인가"]
    X --> G
    G -->|허용| H["Controller — @AuthenticationPrincipal AuthUser"]
    G -->|거부| I["ExceptionTranslationFilter → 401 / 403"]
    H --> J["요청 종료 — SecurityContextHolderFilter.clearContext()"]
```

1. `JwtAuthenticationFilter`가 `resolveToken()`으로 요청 헤더에서 토큰을 꺼내고, `JwtProvider.parse()`가 서명, 만료, 발급자를 검증합니다.
2. 검증에 성공한 토큰의 클레임에서 사용자 ID와 권한을 꺼내 사용자 정보 객체(`AuthUser`)를 만듭니다.
3. 이 객체와 권한 목록으로 `UsernamePasswordAuthenticationToken.authenticated(authUser, null, authUser.getAuthorities())`를 만듭니다. 권한을 함께 넘기면 인증된 상태가 되고, 비밀번호는 필요 없으므로 비워둡니다.
4. `SecurityContextHolder.createEmptyContext()`로 빈 `SecurityContext`를 새로 만들어 `Authentication`을 담고 `SecurityContextHolder.setContext()`로 설정합니다. `SecurityContextHolder`는 기본적으로 `ThreadLocal`을 쓰므로 이 요청을 처리하는 스레드 안에서만 보입니다.
5. 이후 `AuthorizationFilter`가 `SecurityContext`의 권한을 보고 접근 허용 여부를 결정하고, 컨트롤러의 `@AuthenticationPrincipal AuthUser`는 여기서 사용자 정보 객체를 꺼내옵니다.
6. 요청이 끝나면 `SecurityContextHolderFilter`가 `clearContext()`로 `SecurityContext`를 비웁니다. `STATELESS`라 세션도 만들지 않으므로, 다음 요청은 다시 토큰을 보내야 합니다.

**우리 프로젝트에서는**

- 검증에 실패해도 필터는 요청을 막지 않고 실패 원인만 기록합니다. 거부 여부는 경로 규칙이 정합니다(「인증·인가 — 토큰 검증 실패 처리 정책」).
- 인증 객체는 토큰 클레임만으로 만들고 DB를 다시 조회하지 않습니다. 비밀번호가 필요 없어서 `AuthUser`에는 비밀번호 필드가 없습니다.
- 6번은 `AuthenticationScenarioTest`의 `정상_인증_요청_직후_토큰_없는_요청은_401`이 확인합니다. 첫 요청 직후 스레드의 `SecurityContext`가 비어 있고, 두 번째 요청은 401입니다.
- 코드: `global/security/jwt/JwtAuthenticationFilter.java`, `global/security/AuthUser.java`

**참고** [Spring Security 공식 문서 — Persisting Authentication](https://docs.spring.io/spring-security/reference/servlet/authentication/persistence.html)

### 6. 인증과 인가는 어떻게 다르며, 401과 403은 각각 언제 반환하나요?

**핵심** 인증은 "누구인가"를, 인가는 "이 일을 해도 되는가"를 판단하며, 인증에 실패하면 401, 인증은 됐지만 권한이 없으면 403입니다.

**설명**

인증이 먼저이고, 인가는 인증된 사용자를 대상으로 합니다. 401은 인증 실패로, 토큰이 없거나 만료됐거나 변조됐을 때 반환합니다. 상태 이름이 `Unauthorized`라 헷갈리지만 의미는 "인증되지 않음"입니다. 규격상 401에는 `WWW-Authenticate` 헤더를 함께 보내는 것이 원칙입니다. 403은 인증은 됐지만 권한이 없을 때 반환하며, 일반 사용자가 관리자 API를 호출하는 경우입니다. Spring Security에서는 `AuthenticationEntryPoint`가 401을, `AccessDeniedHandler`가 403을 담당합니다.

| 상황 | HTTP 상태 | 처리하는 컴포넌트 | 우리 오류 코드 |
|---|---|---|---|
| 로그인 실패 (없는 계정, 틀린 비밀번호) | 401 | `AuthService.login()` → `GlobalExceptionHandler` | `LOGIN_FAILED` |
| 재발급 실패 (없음·만료·폐기된 리프레시 토큰) | 401 | `AuthService.reissue()` → `GlobalExceptionHandler` | `REFRESH_TOKEN_INVALID` |
| 재발급 실패 (이미 사용된 리프레시 토큰 — 재사용 탐지) | 401 | `AuthService.reissue()` → `GlobalExceptionHandler` | `REFRESH_TOKEN_REUSE_DETECTED` |
| 토큰 없이 보호 API 호출 | 401 | `JwtAuthenticationEntryPoint` | `TOKEN_NOT_EXIST` |
| 만료된 토큰 | 401 | `JwtAuthenticationEntryPoint` | `TOKEN_EXPIRED` |
| 변조되거나 다른 키로 서명한 토큰 | 401 | `JwtAuthenticationEntryPoint` | `TOKEN_INVALID` |
| 일반 사용자가 관리자 API 호출 | 403 | `JwtAccessDeniedHandler` | `ACCESS_DENIED` |
| 토큰 없이 관리자 API 호출 | 401 | `JwtAuthenticationEntryPoint` | `TOKEN_NOT_EXIST` |

토큰 없이, 즉 익명 사용자로 관리자 API를 호출하면 403이 아니라 401이 나옵니다. Spring Security는 익명 사용자의 접근 거부를 권한 부족이 아니라 "인증이 필요함"으로 보고 `AuthenticationEntryPoint`로 넘기기 때문입니다. 이것이 올바른 동작입니다. 이 분기는 `ExceptionTranslationFilter`가 합니다. `AuthorizationFilter`는 두 경우 모두 같은 거부 예외를 던지고, 현재 인증이 익명인지에 따라 401과 403이 갈립니다.

**우리 프로젝트에서는**

- `JwtAuthenticationEntryPoint`는 필터가 기록한 실패 원인에 따라 `TOKEN_NOT_EXIST`, `TOKEN_EXPIRED`, `TOKEN_INVALID` 중 하나로 응답하고 `WWW-Authenticate: Bearer` 헤더를 붙입니다.
- 두 핸들러 모두 `SecurityErrorResponder`로 컨트롤러 오류와 같은 `ApiResponse` JSON을 씁니다.
- 남의 예매에 접근하면 403이 아니라 404 `RESERVATION_NOT_FOUND`입니다. 예매 id가 순차 증가라 403이면 존재 여부가 드러나기 때문입니다.
- 코드: `global/security/handler/JwtAuthenticationEntryPoint.java`, `global/security/handler/JwtAccessDeniedHandler.java`, `global/security/handler/SecurityErrorResponder.java`, `global/config/SecurityConfig.java`

### 7. (선택) OAuth 2.0과 JWT의 역할 차이

**핵심** OAuth 2.0은 권한 위임 절차이고, JWT는 토큰 형식이라 같은 기준의 비교 대상이 아닙니다.

**설명**

OAuth 2.0은 사용자가 비밀번호를 넘기지 않고 제3자 서비스에게 자기 자원에 대한 접근 권한을 위임하는 절차를 정합니다. "이 서비스가 내 이메일에 접근하도록 허용하시겠습니까?"라는 화면이 그 예입니다. 이 절차의 결과로 액세스 토큰이 발급되지만, 그 토큰의 형식은 OAuth 2.0이 정하지 않습니다. JWT일 수도, 무작위 문자열일 수도 있습니다. 참고로 OAuth 2.0 위에 "사용자가 누구인지"를 확인하는 기능을 얹은 OpenID Connect는 신원 토큰을 반드시 JWT로 발급합니다.

**우리 프로젝트에서는**

이 프로젝트는 OAuth 2.0이 아닙니다. 우리 서버가 직접 아이디와 비밀번호를 받아 검증하고(`AuthService.login()`), 직접 JWT를 발급합니다(`JwtProvider.createAccessToken()`). 소셜 로그인은 도전 과제로 별도 진행합니다. 코드: `domain/user/service/AuthService.java`

**참고** [RFC 6749 1.4절](https://www.rfc-editor.org/rfc/rfc6749#section-1.4)

---

## 인증·인가

3주차에 JWT 기반 인증을 적용했습니다. 인증 정보는 `Authorization: Bearer` 헤더로만 받고, 세션과 인증 쿠키는 쓰지 않습니다. 보호 API는 요청 본문이나 쿼리의 `userId`를 믿지 않고 토큰에서 꺼낸 사용자 id로만 처리합니다.

### 인증 구조 요약

**로그인 흐름**

`AuthenticationManager`는 `DaoAuthenticationProvider`에 `UserDetailsService`와 `PasswordEncoder`를 직접 넣어 조립했습니다. Spring이 빈을 찾아 연결하는 방식도 결과는 같지만, 어떤 구현으로 비밀번호를 비교하는지가 코드에 드러나지 않기 때문입니다.

```
AuthService.login()                                     ← 우리 코드
 └ authenticationManager.authenticate(unauthenticated token)
    └ ProviderManager → DaoAuthenticationProvider       ← Spring
       ├ LoginUserDetailsService.loadUserByUsername()   ← 우리 구현, Spring이 호출
       ├ passwordEncoder.matches(raw, hash)             ← Spring이 호출
       └ 성공 → authenticated token, eraseCredentials()
 └ jwtProvider.createAccessToken()                      ← 우리 코드
```

`UserDetails`는 둘로 나눴습니다. 로그인 한 번에만 쓰는 `LoginUserDetails`는 비밀번호 해시를 들고 있다가 인증이 끝나면 지웁니다. 토큰 검증 뒤의 요청은 비밀번호 필드가 아예 없는 `AuthUser(userId, role)`를 principal로 씁니다.

**토큰 Claim 구성**

| Claim | 값 | 비고 |
|---|---|---|
| `sub` | userId 문자열 | loginId가 아니라 id를 실어 요청마다 DB를 조회하지 않습니다 |
| `role` | `USER` \| `ADMIN` | 접두사 없는 도메인 값입니다. `ROLE_`은 `Role.getAuthority()` 한 곳에서만 붙습니다 |
| `iss` | `cgv-api` | 코드 상수. 다른 발급자의 토큰은 거부합니다 |
| `iat` / `exp` | 발급·만료 시각 | 유효기간은 `JWT_ACCESS_TOKEN_VALIDITY`로 정합니다 |

서명은 HS256만 허용합니다. 헤더의 `alg`는 토큰을 만든 쪽이 정하는 값이므로, 서버가 허용할 알고리즘을 따로 못박아 두지 않으면 공격자가 고른 알고리즘(`none` 포함)으로 검증하게 됩니다. principal은 토큰 claim만으로 만들고 DB를 다시 보지 않습니다. 그래서 권한 변경이나 탈퇴는 토큰이 만료될 때까지 반영되지 않으며, 짧은 유효기간이 유일한 완화 수단입니다.

**필터 동작 순서**

`JwtAuthenticationFilter`는 빈으로 등록하지 않고 `SecurityConfig`에서 직접 생성해 Security 체인에만 넣었습니다. `@Component`로 두면 Boot가 서블릿 필터로도 자동 등록해 한 요청에서 두 번 실행됩니다. 실제 체인 순서는 다음과 같습니다.

```
 1 DisableEncodeUrlFilter
 2 WebAsyncManagerIntegrationFilter
 3 SecurityContextHolderFilter          요청 끝에 clearContext()
 4 HeaderWriterFilter
 5 JwtAuthenticationFilter              ← 토큰 검증, 성공 시 SecurityContext에 AuthUser
 6 RequestCacheAwareFilter
 7 SecurityContextHolderAwareRequestFilter
 8 AnonymousAuthenticationFilter        인증이 비어 있으면 익명 토큰
 9 SessionManagementFilter
10 ExceptionTranslationFilter           EntryPoint(401) / AccessDeniedHandler(403) 호출
11 AuthorizationFilter                  경로 규칙 판정
```

JWT 필터는 익명 필터보다 앞에 있어야 토큰 인증이 먼저 자리를 잡습니다. 401과 403을 가르는 것은 `ExceptionTranslationFilter`입니다. `AuthorizationFilter`는 두 경우 모두 같은 `AccessDeniedException`을 던지고, 현재 인증이 익명이면 401, 인증된 사용자면 403으로 갈립니다. 신원을 모르면 권한 없음을 판정할 수 없으므로 익명은 401입니다.

### 공개/보호 경로

위에서부터 처음 맞는 규칙 하나만 적용됩니다.

| 메서드 | 경로 | 규칙 |
|---|---|---|
| * | `/api/admin/**` | `ADMIN`만 |
| GET | `/api/branches/likes`, `/api/movies/likes` | 인증 필요 |
| POST | `/api/auth/signup`, `/api/auth/login`, `/api/auth/reissue`, `/api/auth/logout` | 공개 |
| GET | `/api/movies`, `/api/movies/{id}` | 공개 |
| GET | `/api/branches`, `/api/branches/regions`, `/api/branches/{id}`, `/api/branches/{branchId}/products` | 공개 |
| GET | `/api/screenings`, `/api/screenings/{id}/seats` | 공개 |
| * | `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` | 공개 |
| * | `/error` | 공개 |
| * | 그 외 (예매·결제·취소·내역, 찜 등록·해제, 매점 구매·내역) | 인증 필요 |

- 찜 목록 규칙을 공개 GET보다 먼저 둔 이유는 `/api/branches/{id}`가 `likes`도 받아들이기 때문입니다. 순서가 바뀌면 찜 목록이 익명에게 열립니다.
- `hasRole("ADMIN")` 대신 `hasAuthority(Role.ADMIN.getAuthority())`를 썼습니다. `hasRole`은 내부에서 `ROLE_`을 다시 붙이므로 접두사를 만드는 곳이 두 군데가 됩니다.
- 기본값은 인증 필요입니다. 규칙 없이 새 API가 추가되면 열리지 않고 잠깁니다.
- `/error`를 여는 이유는 컨테이너 오류 포워드도 인가를 다시 거치기 때문입니다. 막으면 원래 오류가 익명 401로 덮입니다.
- 재발급과 로그아웃은 액세스 토큰이 만료된 뒤에도 불러야 하므로 공개합니다. 자격 증명은 본문의 리프레시 토큰입니다.
- 소유권은 경로 규칙이 아니라 쿼리 조건(`WHERE r.id = :id AND r.user.id = :userId`)으로 검사합니다. 남의 예매는 로딩되지 않으므로 취소·결제 메서드를 부를 엔티티 자체가 없습니다.

### CSRF 비활성화

이 API는 인증 정보를 `Authorization: Bearer` 헤더로만 받고 세션과 인증 쿠키를 쓰지 않습니다. CSRF는 브라우저가 쿠키 같은 자격 증명을 요청에 자동으로 실어 보내는 점을 악용하는 공격인데, Bearer 헤더는 클라이언트 코드가 명시적으로 넣어야만 전송되고 다른 출처의 페이지는 그 토큰을 읽을 수 없어 위조된 요청에 인증이 실리지 않습니다. 그래서 CSRF 보호를 비활성화했습니다. 인증 수단을 쿠키로 바꾸면 이 전제가 깨지므로 다시 켜야 합니다.

### CORS

프런트엔드가 다른 출처(예: `http://localhost:3000`)에서 `Authorization` 헤더를 실어 요청하면, 브라우저는 본 요청 전에 **토큰 없이** `OPTIONS` preflight를 먼저 보냅니다. 이 preflight가 허용 응답을 받지 못하면 본 요청은 아예 보내지지 않습니다.

**문제였던 것:** 이전에는 CORS 설정이 어디에도 없었습니다. 테스트로 재현해 보니 preflight가 모두 401이었습니다. 보호 경로는 토큰이 없어서 401이었고, 공개 경로(`/api/movies`)도 공개 규칙이 `GET`에만 걸려 있어 `OPTIONS`는 기본값인 "인증 필요"에 걸렸습니다. 이렇게 되면 브라우저에서는 어떤 API도 호출할 수 없습니다.

**설정:** `SecurityConfig`에서 `http.cors()`로 Security 필터 체인 안에 CORS를 적용했습니다. CORS 필터는 인가 필터보다 앞에서 preflight에 직접 응답합니다. Spring MVC 쪽 CORS 설정(`WebMvcConfigurer`)만 두면, 인가 필터가 `DispatcherServlet`보다 먼저 실행되므로 preflight가 MVC에 닿기 전에 401로 막힙니다.

| 항목 | 값 | 이유 |
|---|---|---|
| 허용 출처 | `CORS_ALLOWED_ORIGINS` (기본 `http://localhost:3000`) | 와일드카드(`*`)를 쓰지 않고 출처를 정확히 지정합니다 |
| 허용 메서드 | `GET`, `POST`, `DELETE` | 컨트롤러가 실제로 쓰는 메서드만 엽니다 |
| 허용 헤더 | `Authorization`, `Content-Type` | 토큰과 JSON 본문 |
| 노출 헤더 | `WWW-Authenticate` | 401 응답의 인증 스킴을 프런트 코드가 읽을 수 있게 합니다 |
| 자격 증명 | 허용 안 함 | 쿠키를 쓰지 않으므로 `Access-Control-Allow-Credentials`를 보내지 않습니다 |
| preflight 캐시 | 1시간 | 같은 요청마다 preflight를 반복하지 않게 합니다 |

preflight가 열렸다고 본 요청의 인증 규칙이 바뀌지는 않습니다. 토큰 없는 본 요청은 그대로 401입니다. 다만 401 응답에도 CORS 헤더가 붙어서, 브라우저가 오류 본문(`TOKEN_NOT_EXIST` 등)을 프런트에 전달할 수 있습니다.

| 상황 | 결과 | 테스트 |
|---|---|---|
| 허용된 출처의 preflight, 보호 경로 | 200, `Access-Control-Allow-Origin`, 허용 메서드·헤더, 자격 증명 헤더 없음 | `허용된_출처의_preflight는_보호_경로여도_토큰_없이_통과한다` |
| 허용된 출처의 preflight, GET만 공개된 경로 | 200 | `허용된_출처의_preflight는_GET만_공개된_경로에서도_통과한다` |
| 허용하지 않은 출처의 preflight | 403, CORS 헤더 없음 | `허용하지_않은_출처의_preflight는_403` |
| 허용된 출처의 본 요청, 토큰 없음 | 401 `TOKEN_NOT_EXIST` + `Access-Control-Allow-Origin` | `허용된_출처의_본_요청도_토큰이_없으면_401이고_CORS_헤더가_붙는다` |

위 4개는 설정 전에 모두 실패했습니다(preflight 3개는 401, 본 요청은 CORS 헤더 없음).

### 토큰 검증 실패 처리 정책

토큰 검증에 실패해도 필터는 요청을 바로 막지 않습니다. 실패 원인만 기록하고 익명 요청으로 넘기며, 거부 여부는 경로 규칙이 정합니다. 보호 API는 기록된 원인에 따라 `TOKEN_EXPIRED` 또는 `TOKEN_INVALID`로 401을 받고, 공개 조회 API는 토큰이 만료되거나 변조됐어도 익명 사용자로 정상 응답합니다. 공개 API는 사용자 정보를 쓰지 않으므로 잘못된 토큰이 권한을 얻는 경로는 없습니다. 대신 클라이언트는 공개 API 응답만으로는 토큰 만료를 알 수 없고, 보호 API를 호출했을 때 알게 됩니다.

필터가 직접 거부 응답을 쓰는 방식은 택하지 않았습니다. 만료 토큰을 가진 클라이언트가 공개 조회 API까지 막히고, 거부 여부를 정하는 곳이 경로 규칙과 필터 두 군데로 갈립니다. 원인은 request attribute로 넘기고, 보호 경로에서 거부될 때 EntryPoint가 그 값을 읽어 응답 코드를 고릅니다.

| 상황 | 흐름 | 응답 |
|---|---|---|
| 보호 API, 토큰 없음 | 익명 → `AuthorizationFilter` 거부 → EntryPoint (기록 없음) | 401 `TOKEN_NOT_EXIST` |
| 보호 API, 만료 토큰 | 필터가 `TOKEN_EXPIRED` 기록 → 익명 → EntryPoint | 401 `TOKEN_EXPIRED` |
| 보호 API, 변조·다른 키·형식 오류 | 필터가 `TOKEN_INVALID` 기록 → 익명 → EntryPoint | 401 `TOKEN_INVALID` |
| 공개 API, 만료·변조 토큰 | 기록만 남고 공개 규칙 통과 | 200 |
| 관리자 경로, USER 토큰 | 인증됨 → `AuthorizationFilter` 거부 → AccessDeniedHandler | 403 `ACCESS_DENIED` |

- 만료만 따로 알립니다. 클라이언트가 재로그인으로 복구할 수 있는 유일한 경우입니다. 변조와 형식 오류를 나눠 알려주면 공격자에게 "파싱까지는 통과했다"는 단서가 됩니다.
- JJWT는 서명을 먼저 검증하고 그다음 만료를 봅니다. 그래서 만료됐으면서 변조된 토큰은 `TOKEN_INVALID`입니다. "만료" 판정은 우리가 발급한 것이 확실한 토큰에만 붙습니다.
- 필터 단계의 실패는 `DispatcherServlet`에 닿기 전이라 `@RestControllerAdvice`가 잡지 못합니다. `SecurityErrorResponder`가 컨트롤러 오류와 같은 `ApiResponse` 형식으로 응답을 직접 씁니다. 401에는 `WWW-Authenticate: Bearer` 헤더를 붙입니다.

### 오류 코드

모든 실패 응답은 같은 형식입니다.

```json
{ "success": false, "code": "TOKEN_EXPIRED", "message": "만료된 토큰입니다." }
```

| 상황 | HTTP 상태 | 코드 |
|---|---|---|
| 로그인 실패 (없는 계정, 틀린 비밀번호 모두) | 401 | `LOGIN_FAILED` |
| 보호 API에 토큰 없음 | 401 | `TOKEN_NOT_EXIST` |
| 만료된 토큰 | 401 | `TOKEN_EXPIRED` |
| 변조된 토큰, 다른 키로 서명한 토큰, 형식 오류, 허용하지 않은 알고리즘, 다른 발급자 | 401 | `TOKEN_INVALID` |
| 인증은 됐지만 권한 부족 (일반 사용자의 관리자 API 호출) | 403 | `ACCESS_DENIED` |
| 리프레시 토큰이 없음·만료·폐기됨, 또는 액세스 토큰을 재발급 본문에 넣음 | 401 | `REFRESH_TOKEN_INVALID` |
| 이미 사용된 리프레시 토큰으로 재발급 (재사용 탐지, 그 로그인의 토큰 전체 폐기) | 401 | `REFRESH_TOKEN_REUSE_DETECTED` |
| 남의 예매에 접근 (없는 예매와 응답이 같음) | 404 | `RESERVATION_NOT_FOUND` |
| 유효한 토큰이지만 탈퇴 등으로 사용자가 없음 | 404 | `USER_NOT_FOUND` |
| 이미 사용 중인 아이디로 가입 | 409 | `DUPLICATE_LOGIN_ID` |
| 가입·로그인·재발급·로그아웃 요청 형식 오류 | 400 | `INVALID_INPUT_VALUE` |

- 로그인 실패는 계정 유무와 관계없이 본문까지 같습니다. 응답이 다르면 아이디 목록을 대입해 가입된 계정만 추려낼 수 있습니다. 계정이 없을 때도 더미 해시로 비교를 돌려 응답 시간 차이를 줄입니다.
- 남의 예매를 403이 아니라 404로 숨기는 이유는 예매 id가 순차 증가라서입니다. 403이면 id를 차례로 넣어 어떤 예매가 존재하는지, 예매량이 얼마인지 알아낼 수 있습니다.

### 테스트 결과

`@SpringBootTest` + `springSecurity()`를 적용한 MockMvc로 실제 필터 체인을 태웠습니다. `@WithMockUser` 같은 인증 우회는 쓰지 않았습니다. 정상 토큰은 로그인 API 응답에서 받고, 실패 토큰은 실제 `JwtProvider`에 설정만 바꿔 넣어 만듭니다.

- 만료: 같은 키, 발급 시각을 유효기간보다 1분 더 과거로 둔 `Clock`
- 변조: 로그인 토큰의 payload만 `role: USER → ADMIN`으로 바꾸고 서명은 그대로
- 다른 키: claim은 같고 서명키만 다른 `JwtProvider`

변조 토큰은 **내용이 바뀌었는지(무결성)** 를, 다른 키 토큰은 **우리가 서명했는지(발급 주체)** 를 검증합니다.

**AuthenticationScenarioTest** — 로그인·토큰 검증

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 올바른 로그인 정보 | Access Token 발급 | 200, `tokenType: Bearer`, 토큰의 sub·role이 가입한 사용자와 일치 | `올바른_로그인_정보면_Access_Token을_발급한다` |
| 없는 계정 / 잘못된 비밀번호 | 동일한 로그인 실패 응답, 토큰 미발급 | 둘 다 401 `LOGIN_FAILED`, 본문 문자열까지 동일, `data` 없음 | `없는_계정과_틀린_비밀번호의_로그인_실패_응답이_완전히_같다` |
| 토큰 없이 공개 API 호출 | 정상 처리 | 200 | `토큰_없이_공개_API를_호출하면_정상_처리된다` |
| 정상 토큰으로 보호된 API 호출 | 정상 처리 | 200 | `로그인으로_받은_토큰으로_보호된_API를_호출하면_정상_처리된다` |
| 토큰 없이 보호된 API 호출 | 401 + 공통 JSON | 401 `TOKEN_NOT_EXIST`, `WWW-Authenticate: Bearer`, `application/json;charset=UTF-8` | `토큰_없이_보호된_API를_호출하면_401_TOKEN_NOT_EXIST` |
| 만료된 토큰으로 보호된 API 호출 | 401 + 만료 오류 코드 | 401 `TOKEN_EXPIRED` | `만료된_토큰이면_401_TOKEN_EXPIRED` |
| 변조된 토큰으로 보호된 API 호출 | 401 + 유효하지 않은 토큰 오류 코드 | 관리자 API에 401 `TOKEN_INVALID` (200·403 아님) | `payload를_변조한_토큰이면_401_TOKEN_INVALID` |
| 다른 키로 서명한 토큰 | 401 | 401 `TOKEN_INVALID` | `다른_키로_서명한_토큰이면_401_TOKEN_INVALID` |
| 정상 인증 요청 직후, 토큰 없이 보호된 API 호출 | 401 (이전 요청의 인증이 유지되지 않음) | 첫 요청 200 → 스레드의 SecurityContext 비어 있음 → 쿠키·세션 없음 → 두 번째 요청 401 `TOKEN_NOT_EXIST` | `정상_인증_요청_직후_토큰_없는_요청은_401` |
| 변조된 토큰으로 공개 API 호출 | 정상 처리 | 200 (익명) | `공개_API는_변조_토큰을_보내도_익명으로_정상_처리된다` |

**AuthorizationScenarioTest** — 접근 제어

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 일반 사용자로 관리자 API 호출 | 403 + 공통 JSON | 403 `ACCESS_DENIED` | `일반_사용자가_관리자_API를_호출하면_403_ACCESS_DENIED` |
| 관리자로 관리자 API 호출 | 정상 처리 | 200, `role: ADMIN` | `관리자가_관리자_API를_호출하면_정상_처리된다` |
| 사용자 A의 토큰으로 B의 예매 취소 | 거부 + B의 예매 불변 | 404 `RESERVATION_NOT_FOUND`, DB 재조회 시 `RESERVED`·좌석 점유 유지, 이후 B 본인은 취소 성공 | `다른_사용자의_예매를_취소하면_거부되고_DB의_예매_상태는_그대로다` |
| 회원가입 본문에 `"role": "ADMIN"` | USER로 생성 | 201, DB role `USER`, 토큰 role `USER`, 관리자 API 403 | `회원가입_본문에_role_ADMIN을_넣어도_USER로_생성된다` |

세션 4 시점에 `./gradlew test` 전체 230개가 통과했습니다.

### 비밀값 로컬 설정

비밀값은 Git에 올리지 않습니다. `application.yaml`에는 `${...}` 자리표시자만 있고 기본값이 없어서, 값을 넣지 않으면 기동이 실패합니다. 프로젝트 루트의 `.env`(`.gitignore`에 등록됨)에 아래 변수를 적고, 실행 환경(IDE 실행 구성 등)이 이 파일을 환경변수로 읽도록 설정합니다.

| 변수 | 형식 | 비고 |
|---|---|---|
| `JWT_SECRET` | Base64 문자열, 디코딩 후 256비트 이상 | `openssl rand -base64 32`로 생성합니다. 짧거나 Base64가 아니면 `jwt.secret`을 가리키는 메시지와 함께 기동이 실패합니다 |
| `JWT_ACCESS_TOKEN_VALIDITY` | Duration (예: `30m`) | 로그아웃해도 만료 전까지 유효하므로 짧게 둡니다 |
| `REFRESH_TOKEN_VALIDITY` | Duration (예: `14d`) | 액세스 토큰보다 길게 둡니다. 없으면 기동이 실패합니다 |
| `SPRING_PROFILES_ACTIVE` | `local` | 관리자 계정 초기화는 local 프로필에서만 동작합니다 |
| `ADMIN_LOGIN_ID` | 가입 규칙과 같은 영문 소문자·숫자 4~20자 권장 | local에서 비어 있으면 기동이 실패합니다 |
| `ADMIN_PASSWORD` | 평문 | 기동 시 BCrypt로 해시해 저장합니다 |
| `CORS_ALLOWED_ORIGINS` | 출처 목록, 쉼표로 구분 (예: `http://localhost:3000,https://cgv.example`) | 비밀값은 아닙니다. 없으면 `http://localhost:3000`만 허용합니다 |
| `TOSS_SECRET_KEY` | 토스페이먼츠 결제위젯 연동 시크릿 키 (`test_gsk_…`) | 서버 전용입니다. 승인·조회·취소 API의 Basic 인증에만 쓰고 어떤 응답에도 담지 않습니다 |
| `TOSS_CLIENT_KEY` | 같은 연동 키의 클라이언트 키 (`test_gck_…`) | 브라우저에 노출되는 값이지만 시크릿 키와 짝이 맞아야 해서 같은 곳에서 관리합니다. `GET /api/payments/widget-config`로 내려주므로 페이지에 직접 적지 않습니다 |

- 관리자 계정은 data.sql이 아니라 `ApplicationRunner`가 만듭니다. data.sql에는 해시를 박아야 하는데, 해시도 오프라인 대입 공격의 대상입니다. 이미 있으면 건너뜁니다.
- 운영에서 부팅 부수효과로 관리자가 생기면 안 되므로 `@Profile("local")`로 제한했습니다.
- 토스 키는 테스트 키만 씁니다. 지금 쓰는 키는 개발자센터가 전자결제 신청 전에 보여주는 **문서 공용 테스트 키**입니다. 여러 사람이 함께 쓰는 상점이라 개발자센터에서 결제 내역을 볼 수 없고, `orderId` 조회와 웹훅 등록도 되지 않습니다. 결제 상태는 우리 DB와 관리자 대조 API로 확인합니다(「결제 — 결제 조회」).
- 테스트는 `src/test/resources/application.yaml`의 테스트 전용 더미 키를 쓰므로 별도 설정 없이 실행됩니다.

---

## 리프레시 토큰

3주차 도전 과제입니다. 액세스 토큰이 만료될 때마다 다시 로그인하지 않도록, 로그인 시 리프레시 토큰을 함께 발급하고 그것으로 액세스 토큰을 재발급합니다. 저장소는 기존 DB를 씁니다. 재발급할 때마다 리프레시 토큰도 새로 바꾸고(순환 발급), 이미 쓴 리프레시 토큰이 다시 들어오면 탈취로 보고 그 로그인의 토큰을 모두 폐기합니다(재사용 탐지).

### 발급·재발급·폐기 흐름

```
로그인    POST /api/auth/login     {loginId, password}
          → 200 {accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn}
          → refresh_token에 해시·만료 시각·새 묶음 id(family_id) 저장 (원문은 응답으로 한 번만)

보호 API  Authorization: Bearer {accessToken}
          → 만료되면 401 TOKEN_EXPIRED

재발급    POST /api/auth/reissue   {refreshToken}
          → 해시로 조회하며 행 잠금 → 사용 완료? → 폐기? → 만료?
          → 보낸 토큰 used_at 기록 + 같은 묶음·같은 만료 시각의 새 토큰 저장 → DB 사용자의 id·role로 새 accessToken
          → 200 {accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn}

로그아웃  POST /api/auth/logout    {refreshToken}
          → 보낸 토큰이 속한 묶음 전체의 revoked_at 기록 → 200 (없는 토큰이어도 200)
```

| 메서드 | 경로 | 인증 | 요청 | 응답 | 오류 |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | 공개 | `{loginId, password}` | 200 `{accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn}` | 400 `INVALID_INPUT_VALUE` · 401 `LOGIN_FAILED` |
| POST | `/api/auth/reissue` | 공개 | `{refreshToken}` | 200 `{accessToken, tokenType, expiresIn, refreshToken, refreshTokenExpiresIn}` | 400 `INVALID_INPUT_VALUE` · 401 `REFRESH_TOKEN_INVALID` · 401 `REFRESH_TOKEN_REUSE_DETECTED` |
| POST | `/api/auth/logout` | 공개 | `{refreshToken}` | 200 | 400 `INVALID_INPUT_VALUE` |

- 리프레시 토큰은 쿠키가 아니라 응답·요청 본문으로 주고받습니다. 인증 정보를 헤더와 본문으로만 다룬다는 전제(CSRF 비활성화의 근거)를 유지하기 위해서입니다.
- 재발급과 로그아웃은 액세스 토큰이 만료된 뒤에도 불러야 하므로 공개 경로입니다. 만료된 액세스 토큰을 헤더에 남겨 둔 채 불러도, 필터가 실패 원인만 기록하고 통과시키므로 막히지 않습니다.
- 한 사용자가 여러 기기에서 로그인하면 로그인마다 토큰 묶음이 따로 생깁니다. 로그아웃과 재사용 탐지는 그 로그인의 묶음만 폐기하고 다른 기기의 묶음은 건드리지 않습니다.

### 클라이언트가 지켜야 할 것

- 재발급 응답에 새 리프레시 토큰(`refreshToken`, `refreshTokenExpiresIn`)이 함께 옵니다. 재발급에 성공하면 가지고 있던 리프레시 토큰을 **즉시 새 값으로 바꿔 저장**해야 합니다. 보낸 토큰은 사용 완료 상태가 되어 다시 쓸 수 없습니다.
- 사용 완료된 토큰을 다시 보내면 탈취로 간주되어 401 `REFRESH_TOKEN_REUSE_DETECTED`가 오고, 이 로그인의 모든 리프레시 토큰이 폐기됩니다. 이때는 다시 로그인하고, 사용자에게 계정 보안(비밀번호 변경 등)을 안내하는 것이 좋습니다.
- **로그인 한 번의 리프레시 토큰 수명은 고정입니다.** 재발급해도 만료 시각은 늘어나지 않고 로그인한 시점부터 계산하므로, 계속 사용 중이더라도 `REFRESH_TOKEN_VALIDITY`가 지나면 다시 로그인해야 합니다. 재발급 응답의 `refreshTokenExpiresIn`은 남은 시간(초)입니다.
- **클라이언트는 재발급 요청을 동시에 여러 개 보내지 말고 하나로 묶어 보내야 합니다.** 여러 API 호출이 동시에 401 `TOKEN_EXPIRED`를 받더라도 재발급은 한 번만 보내고, 나머지 호출은 그 결과를 기다렸다가 새 액세스 토큰으로 다시 보내야 합니다. 같은 리프레시 토큰으로 재발급이 두 번 가면 두 번째가 재사용으로 탐지되어 로그인이 풀립니다.
- 재발급 응답을 받지 못한 채(네트워크 오류) 같은 토큰으로 다시 보내는 경우도 서버는 재사용과 구분할 수 없습니다. 이 경우 다시 로그인해야 합니다. 이유는 아래 「동시 요청 처리 방식과 한계」에 있습니다.

### 저장 방식

**원문은 JWT가 아니라 256비트 무작위 문자열입니다.** `SecureRandom`으로 32바이트를 만들고 패딩 없는 `Base64URL`로 인코딩해 43자가 됩니다. 폐기 여부를 어차피 DB에서 확인해야 하므로 토큰 안에 정보를 담을 이유가 없습니다. 오히려 JWT로 만들면, 같은 키로 서명된 이상 액세스 토큰 필터를 통과할 위험이 생깁니다.

**DB에는 원문이 아니라 SHA-256 해시만 저장합니다.** DB가 유출돼도 해시로는 재발급을 요청할 수 없습니다. 비밀번호처럼 솔트를 쓰는 느린 해시(BCrypt)를 쓰지 않은 이유는 두 가지입니다.

| 관점 | 비밀번호 (BCrypt) | 리프레시 토큰 (SHA-256) |
|---|---|---|
| 조회 | 솔트 때문에 같은 입력도 해시가 매번 달라 해시로 행을 찾을 수 없습니다. 로그인 아이디로 먼저 찾습니다 | 같은 입력은 항상 같은 해시라 `token_hash` 유니크 인덱스로 바로 찾습니다 |
| 사전 공격 | 사람이 고른 값이라 흔한 후보가 있어, 해시가 유출되면 후보를 대입해 볼 수 있습니다. 느리게 만들어 막습니다 | 256비트 난수라 대입할 후보 목록 자체가 없습니다. 해시가 빠르든 느리든 역산할 수 없습니다 |

이 판단은 토큰이 충분히 길다는 전제에서만 성립합니다. 토큰을 짧게 줄이면 빠른 해시를 모든 후보에 대입해 볼 수 있게 되므로, 길이와 해시 방식은 함께 정해야 합니다.

**순환 발급과 재사용 탐지를 위해 컬럼 두 개를 둡니다.**

| 컬럼 | 의미 |
|---|---|
| `family_id` char(36) | 로그인할 때 만든 UUID. 순환으로 생긴 토큰은 부모의 값을 물려받아, 같은 로그인에서 이어진 토큰을 한 묶음으로 묶습니다. 묶음 단위 폐기의 조회 조건이라 인덱스(`idx_refresh_token_family`)를 둡니다. 인덱스가 없으면 InnoDB가 테이블 전체를 훑으며 지나간 행을 모두 잠가, 한 사용자의 폐기가 모든 사용자의 재발급을 막습니다 |
| `used_at` datetime | 이 토큰으로 다음 토큰을 받아 간 시각. 값이 있는 토큰이 다시 들어오면 재사용입니다 |

폐기는 행 삭제가 아니라 `revoked_at` 기록입니다. 삭제하면 폐기된 토큰과 처음부터 없던 토큰을 구분할 수 없고, 재사용 탐지의 근거도 사라집니다.

**만료된 행은 로그인할 때 지웁니다.** 순환할 때마다 행이 하나씩 늘어나므로, 지우지 않으면 테이블이 계속 커집니다. 별도 스케줄러를 두지 않고, 로그인(발급) 트랜잭션에서 그 사용자의 만료된 행만 지웁니다. 그래서 사용자 한 명이 가진 행은 마지막 로그인 이후 유효기간(14일) 안에 생긴 것으로 제한됩니다.

- 순환으로 생긴 토큰은 부모의 만료 시각을 물려받습니다. 그래서 한 묶음은 함께 만료되고 함께 지워지며, 반쯤 지워진 묶음이 남지 않습니다.
- 지워진 토큰으로 재발급을 요청하면 `not_found`가 되어 같은 401 `REFRESH_TOKEN_INVALID`를 받습니다. 만료된 묶음에서는 재사용 탐지를 잃지만, 그 묶음에는 이미 쓸 수 있는 토큰이 없어서 막아 줄 것이 없습니다.
- 지울 행의 id를 잠금 없는 읽기로 먼저 고른 뒤 기본키로 지웁니다. `user_id` 범위로 바로 `DELETE`하면 InnoDB가 그 범위의 간격까지 잠급니다. 그러면 같은 사용자가 두 기기에서 동시에 로그인할 때 서로의 `INSERT`를 막아 교착(deadlock)이 납니다. MySQL 컨테이너에서 같은 사용자의 발급 10건을 동시에 보내 비교했습니다.

  | 삭제 방식 | 만료 행 0개 | 만료 행 3개 |
  |---|---|---|
  | `user_id` 범위 `DELETE` (대조 실험) | 3회 모두 교착, 마지막 회 10건 중 9건 실패 | 3회 모두 통과 |
  | id 조회 후 기본키 `DELETE` (현재) | 5회 모두 통과 | 5회 모두 통과 |

  교착은 지울 행이 **없을** 때 납니다. 잠글 행이 없으면 양쪽이 간격 잠금만 쥔 채 같은 간격에 `INSERT`하려 하기 때문입니다. 지울 행이 있으면 그 행의 잠금에서 차례를 기다려 교착까지 가지 않습니다. 처음 로그인하거나 만료 행이 없는 사용자가 흔한 경우라 실제로 일어날 수 있는 상황입니다.
- 만료 행 조회는 `user_id` FK 인덱스를 탑니다(`EXPLAIN`: `type=ref`, `key=FK…`). 사용자당 행 수가 위처럼 제한되므로 `expires_at` 인덱스는 따로 두지 않았습니다.
- 한계: 다시 로그인하지 않는 사용자의 만료 행은 남습니다. 사용자 수에 비례하는 만큼은 남는다는 뜻이고, 이것까지 줄이려면 주기적인 일괄 삭제가 필요합니다.

- 코드: `global/security/refresh/RefreshTokenProvider.java`, `domain/user/entity/RefreshToken.java`, `domain/user/service/RefreshTokenService.java`, `domain/user/service/AuthService.java`

### 액세스 토큰과의 구분

두 토큰은 받는 곳과 검증 수단이 달라 서로의 자리에서 쓰일 수 없습니다. 이를 위한 별도의 "토큰 종류 검사" 코드는 없습니다.

| 경로 | 받는 곳 | 검증 수단 | 다른 토큰을 넣으면 |
|---|---|---|---|
| 보호 API | `Authorization` 헤더 | 서명 (`JwtProvider.parse()`) | 리프레시 토큰은 점이 없어 JWT 세 조각으로 나뉘지 않습니다. 형식 오류 → 401 `TOKEN_INVALID` |
| 재발급 | 요청 본문 | DB 조회 (`findByTokenHashForUpdate()`) | 액세스 토큰은 `refresh_token` 테이블에 저장된 적이 없습니다. 조회 실패 → 401 `REFRESH_TOKEN_INVALID` |

그래서 액세스 토큰만 탈취된 경우, 공격자는 재발급으로 수명을 늘릴 수 없고 그 토큰의 `exp`까지만 쓸 수 있습니다.

### 순환 발급과 재사용 탐지

**재발급 처리 순서.** 아래 전체가 `RefreshTokenService.reissue()` 한 트랜잭션입니다.

```
1. 받은 토큰을 해시해 SELECT ... FOR UPDATE        없으면 → 401 REFRESH_TOKEN_INVALID
2. 사용 완료?  → 묶음 전체 revoked_at 기록 + 경고 로그 → 401 REFRESH_TOKEN_REUSE_DETECTED (폐기는 커밋)
3. 폐기?       → 401 REFRESH_TOKEN_INVALID
4. 만료?       → 401 REFRESH_TOKEN_INVALID
5. 보낸 토큰 used_at 기록, 같은 family_id·같은 expires_at으로 새 토큰 INSERT
6. DB 사용자의 id·role로 새 액세스 토큰 → 커밋(잠금 해제) → 200
```

- 5단계의 사용 완료 표시와 새 토큰 저장은 한 트랜잭션이라, 어느 쪽이 실패해도 둘 다 되돌아갑니다. "사용 완료인데 다음 토큰이 없는" 상태가 생기지 않고, 클라이언트는 같은 토큰으로 다시 시도할 수 있습니다.
- 사용 완료를 폐기보다 먼저 확인합니다. 재사용 탐지로 묶음이 폐기되면 사용 완료 토큰도 폐기 상태가 되는데, 그 뒤에 같은 토큰이 또 와도 일반 거부가 아니라 재사용으로 기록해야 하기 때문입니다. 사용된 적 없이 폐기된 토큰(로그아웃했거나 탐지로 함께 폐기된 최신 토큰)은 일반 거부입니다.
- 2단계는 폐기를 기록한 뒤 예외를 던집니다. 예외가 나면 트랜잭션이 롤백되는 것이 기본이라, 그대로 두면 401은 나가지만 폐기는 사라집니다. 그래서 이 메서드만 `@Transactional(noRollbackFor = CustomException.class)`로 둡니다. 이 메서드에서 쓰기 뒤에 예외를 던지는 곳은 2단계뿐입니다.
- 폐기만 새 트랜잭션(`REQUIRES_NEW`)으로 분리하는 방법은 쓸 수 없습니다. 바깥 트랜잭션이 1단계에서 잡은 행 잠금을 안쪽 트랜잭션의 UPDATE가 기다리고, 바깥은 안쪽이 끝나기를 기다려 스스로 멈춥니다. 한쪽 대기가 DB가 아니라 애플리케이션 호출 스택에 있어 InnoDB 교착 탐지에도 걸리지 않고, 잠금 대기 한도(3초)까지 커넥션 두 개를 쥔 채 기다리다 실패합니다.

**순환 발급이 있을 때와 없을 때, 리프레시 토큰을 훔친 공격자가 쓸 수 있는 기간**

| | 순환 발급 없음 | 순환 발급 + 재사용 탐지 |
|---|---|---|
| 공격자가 쓸 수 있는 기간 | 토큰 만료까지(`REFRESH_TOKEN_VALIDITY`, 예: 14일). 정상 사용자와 공격자가 같은 토큰을 나란히 써도 서버에는 구분할 신호가 없습니다 | 둘 중 누구든 상대가 이미 쓴 토큰을 내는 순간 끝납니다. 앱을 쓰는 정상 사용자는 액세스 토큰 수명마다 재발급하므로, 대략 액세스 토큰 수명 안에 탐지됩니다 |
| 최악의 경우 | 같음 | 공격자가 먼저 순환한 뒤 정상 사용자가 앱을 쓰지 않으면 탐지 신호가 없습니다. 이때도 만료 시각을 물려받으므로 로그인 시점 + `REFRESH_TOKEN_VALIDITY`를 넘지 못합니다 |

재발급할 때마다 만료를 새로 늘려 주면, 토큰을 훔친 쪽이 계속 순환해 무기한 쓸 수 있습니다. 그래서 수명은 로그인 시점 기준으로 고정합니다. 두 경우 모두 이미 발급된 액세스 토큰은 만료 전까지 유효합니다(아래 「한계」).

**누가 공격자인지 모르는데 왜 묶음 전체를 폐기하는가.** 서버가 아는 것은 "같은 토큰 계보를 두 곳에서 쓰고 있다"는 사실뿐이고, 두 요청은 바이트 단위로 같습니다. 들어온 토큰 하나만 막으면, 공격자가 먼저 순환한 경우 늦게 온 정상 사용자를 쫓아내고 공격자를 남기게 됩니다. 묶음 전체를 폐기하면 공격자는 반드시 밀려납니다. 정상 사용자도 함께 밀려나지만, 다시 들어올 수 있는 쪽은 비밀번호를 아는 정상 사용자뿐입니다. 다른 기기의 묶음은 이 탈취와 무관하게 발급된 것이라 그대로 둡니다.

**로그아웃도 묶음 전체를 폐기합니다.** 공격자가 훔친 토큰으로 먼저 재발급을 받으면 정상 사용자가 가진 것은 이미 사용 완료된 토큰입니다. 그 행 하나만 폐기하면 공격자가 가진 최신 토큰은 만료 시각까지 살아남습니다. 사용 완료 토큰으로 로그아웃해도 200을 주되 서버 로그에 경고를 남깁니다. 로그아웃이 토큰 상태를 확인하는 창구가 되지 않게 하기 위해서입니다.

**서버 로그**(원문 토큰은 쓰지 않습니다)

```
[RefreshToken] 재사용 탐지 userId=5 familyId=724b… tokenId=8 usedAt=… elapsedMs=4 revoked=2
[RefreshToken] 사용 완료 토큰으로 로그아웃 userId=30 familyId=4a23… tokenId=33 usedAt=… revoked=2
[RefreshToken] 재발급 거부 reason=not_found|revoked|expired|lock_timeout tokenId=…
```

`elapsedMs`는 토큰이 사용된 뒤 다시 들어오기까지 걸린 시간입니다. 수 밀리초면 클라이언트의 중복 요청, 수 분 이상이면 탈취였을 가능성이 크다는 사후 판단 근거가 됩니다.

### 동시 요청 처리 방식과 한계

**방식: 토큰 행 비관적 잠금.** 같은 리프레시 토큰으로 요청 두 개가 거의 동시에 오면 다음 순서로 처리됩니다.

```
요청1: SELECT ... WHERE token_hash=? FOR UPDATE  → 잠금 획득, used_at = null
요청2: SELECT ... WHERE token_hash=? FOR UPDATE  → 대기 (findByTokenHashForUpdate 호출에서 멈춤)
요청1: used_at 기록, 새 토큰 INSERT, 커밋        → 잠금 해제, 200
요청2: 재개. 잠금 읽기는 스냅샷이 아니라 최신 커밋본을 읽어 used_at이 채워진 것을 봄
       → 사용 완료 → 묶음 전체 폐기(요청1이 방금 받은 새 토큰 포함) → 401 REFRESH_TOKEN_REUSE_DETECTED
```

- 잠금 조회에는 `users`를 조인하지 않습니다. MySQL의 `FOR UPDATE`는 조인한 행까지 잠가, 같은 사용자의 다른 기기 재발급까지 줄을 세웁니다. 사용자는 잠금 뒤에 따로 읽습니다.
- 매점 재고 차감과 같은 기준입니다. 경합 단위(토큰 한 개)와 정확히 맞는 행이 있고, 상태 판정 규칙이 엔티티 메서드에 있어서 조건부 UPDATE로 규칙을 쿼리에 한 벌 더 두지 않았습니다. 좌석에서 비관적 잠금을 쓰지 않은 이유는 잠글 행이 없어서였는데, 여기는 해당하지 않습니다.
- 잠금을 기다리다 대기 한도를 넘기면 401 `REFRESH_TOKEN_INVALID`로 응답합니다. 409로 재시도를 안내하면 그 재시도는 사용 완료 토큰이 되어 재사용으로 탐지되기 때문입니다. 이 변환은 트랜잭션이 끝난 뒤 `AuthService`에서 합니다. 트랜잭션 안에서 잡으면, 트랜잭션이 이미 롤백 전용으로 표시되었는지에 따라 커밋 시점에 500(`UnexpectedRollbackException`)이 될 수 있습니다.

**트랜잭션 격리 수준만으로는 막을 수 없는 이유.** MySQL의 기본 격리 수준(REPEATABLE READ)에서 일반 `SELECT`는 잠금 없이 스냅샷을 읽습니다. 두 요청 모두 "아직 사용 안 됨"을 보고, 두 번째 요청의 UPDATE는 첫 요청의 커밋까지 기다린 뒤 **판정을 다시 하지 않고 덮어씁니다.** 결국 새 토큰이 둘 생겨 묶음이 두 갈래로 갈라집니다. SERIALIZABLE로 올리면 `SELECT`가 공유 잠금이 되어 두 요청이 서로의 쓰기를 막는 교착 상태가 되고, 그중 하나가 강제 롤백됩니다. 막히기는 하지만 교착 오류에 기대는 방식이고, 전역 설정이라 다른 API까지 느려집니다. 문제는 격리 수준이 아니라 "확인한 값이 쓸 때까지 유지되는가"여서, 확인하는 읽기 자체를 잠금 읽기로 만들었습니다.

**한계: 유예 시간을 두지 않습니다.** 같은 토큰이 짧은 간격으로 두 번 오면 네트워크 재시도든 탈취든 똑같이 재사용으로 처리합니다. 그래서 동시에 두 번 보낸 정상 클라이언트나 첫 응답을 받지 못하고 재시도한 클라이언트도 다시 로그인해야 합니다.

| 유예 시간을 둔다면 | 문제 |
|---|---|
| 유예 시간 안의 재요청에 같은 새 토큰을 다시 준다 | 원문을 저장해야 합니다. "해시만 저장" 원칙과 충돌합니다 |
| 유예 시간 안의 재요청에 새 토큰을 하나 더 준다 | 묶음이 두 갈래로 갈라집니다. 유예 시간 안에 들어온 공격자도 살아 있는 토큰을 얻습니다 |
| 유예 시간 안에는 탐지 없이 일반 거부만 한다 | 공격자가 정상 사용자보다 먼저 순환하고 정상 사용자가 몇 초 뒤 따라오면, 정상 사용자만 튕기고 탐지가 일어나지 않아 공격자가 묶음을 만료까지 차지합니다. 탈취한 요청을 즉시 재전송하는 공격이 정확히 이 구간에 들어옵니다 |

오탐의 대가는 재로그인 한 번이고, 놓쳤을 때의 대가는 최대 `REFRESH_TOKEN_VALIDITY` 동안의 탈취입니다. 그래서 유예 시간 없이 탐지를 우선하고, 대신 클라이언트가 재발급 요청을 하나로 묶어 보내도록 안내합니다.

### 거부 정책

- 없음, 만료, 폐기, 액세스 토큰을 넣은 경우를 모두 `REFRESH_TOKEN_INVALID` 하나로 응답합니다. 어느 경우든 클라이언트가 할 일은 다시 로그인하는 것 하나뿐입니다. 응답으로 원인을 구분하면 "이 토큰이 한때 유효했는지", "사용자가 로그아웃했는지"를 알 수 있게 됩니다.
- 재사용만 `REFRESH_TOKEN_REUSE_DETECTED`로 구분합니다. 재로그인에 더해 "탈취 가능성을 사용자에게 알린다"는 할 일이 하나 더 있기 때문입니다. 공격자에게는 탐지됐다는 사실만 드러나는데, 이미 쓸 수 있는 토큰이 없어 얻는 것이 없습니다.
- 발급한 토큰은 항상 43자입니다. 길이가 다르면 해시와 DB 조회 없이 같은 `REFRESH_TOKEN_INVALID`로 거부합니다(로그아웃은 200). 길이 제한이 없으면 수 MB 문자열도 전부 해시하고 DB까지 조회하게 됩니다. `@Size` 검증으로 400을 내지 않은 이유는 두 가지입니다. 거부 응답을 원인과 관계없이 같게 두기 위해서이고, 검증 오류 응답이 받은 값을 그대로 되돌려 주기 때문입니다. 본문 크기 자체는 JSON을 읽기 전에 막아야 하므로 앞단 프록시(예: nginx `client_max_body_size`)의 몫입니다.
- 원인은 서버 로그에만 남깁니다(`reason=length_mismatch / not_found / revoked / expired / lock_timeout`, 원문 대신 행 번호).
- 로그아웃은 없는 토큰, 이미 폐기된 토큰, 사용 완료 토큰에도 200입니다. 클라이언트는 결과와 관계없이 토큰을 버리므로 실패를 알려도 할 일이 없고, 로그아웃 API가 토큰 유효성을 확인하는 창구가 되지도 않습니다. 이미 폐기된 토큰의 폐기 시각은 덮어쓰지 않습니다.
- 재발급 실패는 컨트롤러에서 전역 예외 처리로 응답합니다. 필터 단계가 아니므로 `WWW-Authenticate` 헤더는 붙지 않으며, `LOGIN_FAILED`와 같은 동작입니다.

### 한계 — 로그아웃해도 액세스 토큰은 만료까지 유효합니다

액세스 토큰 검증은 서명과 만료만 보고 DB를 조회하지 않습니다. 로그아웃과 재사용 탐지가 바꾸는 것은 `refresh_token` 테이블뿐이라 두 경로가 만나는 곳이 없습니다. 그래서 폐기 직전에 발급된 액세스 토큰은 `exp`까지 계속 보호 API를 호출할 수 있습니다. 재사용이 탐지돼도 공격자가 이미 받아 둔 액세스 토큰은 마찬가지입니다. 이 동작은 테스트(`로그아웃_후에도_액세스_토큰은_만료_전까지_유효하다`)로 명시해 두었습니다.

| 완화 방법 | 필요한 것 | 비용 | 적용 |
|---|---|---|---|
| 액세스 토큰 수명 단축 | `JWT_ACCESS_TOKEN_VALIDITY`를 짧게 | 재발급 요청이 늘어납니다. 즉시 무효화는 아닙니다 | 적용 |
| 액세스 토큰 차단 목록 | 토큰마다 고유 id(`jti`)를 넣고, 로그아웃 시 그 id를 만료까지 저장해 필터가 매 요청 조회 | 요청마다 저장소 조회. 서명만으로 끝나는 검증의 장점을 일부 내줍니다 | 범위 밖 |
| 사용자별 무효화 시각 | 사용자에 "이 시각 이전 발급분은 무효"를 기록하고 필터가 토큰의 `iat`와 비교 | 요청마다 사용자 조회. 기기별이 아니라 전체 기기에 적용됩니다 | 범위 밖 |

### 테스트 결과

`RefreshTokenScenarioTest`는 세션 4와 같이 `springSecurity()`를 적용한 MockMvc로 실제 필터 체인을 태우고, 토큰은 로그인 API로 받습니다. 만료된 리프레시 토큰은 시간을 기다리지 않고, 만료 시각을 과거로 둔 행을 해시와 함께 직접 저장해 만듭니다. 테스트 설정의 리프레시 토큰 수명은 14일(1,209,600초)입니다.

`RefreshTokenConcurrencyTest`는 테스트 트랜잭션 없이 돌아, 요청마다 트랜잭션이 따로 열리고 커밋됩니다. 테스트 트랜잭션 안에서는 모든 요청이 한 트랜잭션에 합류해 잠금 경쟁이 생기지 않고, 롤백될 변경도 같은 트랜잭션에서는 보여 "폐기가 커밋되지 않는" 버그가 드러나지 않기 때문입니다. 동시 요청은 `ExecutorService`와 `CountDownLatch`로 모든 스레드를 준비시킨 뒤 한 번에 출발시켜 만들고, 결과는 새 트랜잭션에서 DB를 다시 읽어 확인합니다. 이 클래스가 만든 사용자와 토큰은 각 테스트 뒤에 지웁니다.

**순환 발급과 재사용 탐지 시나리오**

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 재발급 | 새 액세스 토큰과 새 리프레시 토큰 발급, 이전 리프레시 토큰은 사용 완료 | 200, 새 43자 토큰, 이전 토큰 `used_at` 기록·미폐기, 새 토큰은 같은 묶음·같은 만료 시각, `refreshTokenExpiresIn` ≤ 1209600 | `재발급하면_새_리프레시_토큰을_주고_이전_토큰은_사용_완료된다` |
| 새 리프레시 토큰으로 다시 재발급 | 정상 | 200, 세 번째 토큰은 앞의 둘과 다름, 새 액세스 토큰으로 보호 API 200 | `새_리프레시_토큰으로_다시_재발급할_수_있다` |
| 사용 완료된 이전 리프레시 토큰으로 재발급 | 401 + 재사용 탐지 오류 코드 | 401 `REFRESH_TOKEN_REUSE_DETECTED`, DB 재조회로 묶음 2행 모두 폐기 | `사용_완료된_리프레시_토큰으로_재발급하면_재사용_탐지` |
| 재사용 탐지 직후, 같은 묶음의 최신 리프레시 토큰으로 재발급 | 401 (묶음 전체 폐기 확인) | 401 `REFRESH_TOKEN_INVALID`, 묶음 3행 모두 폐기 | `재사용_탐지_후_같은_묶음의_최신_토큰은_401` |
| 재사용 탐지 후, 다른 로그인에서 받은 리프레시 토큰으로 재발급 | 정상 (다른 묶음은 영향 없음) | 200, 다른 묶음은 폐기되지 않음 | `재사용_탐지는_다른_로그인의_묶음에_영향이_없다` |
| 같은 리프레시 토큰으로 동시 재발급 10건 | 정확히 1건만 성공 | 200 1건, 401 `REFRESH_TOKEN_REUSE_DETECTED` 9건, 묶음 2행(갈라지지 않음), 성공한 1건이 받은 새 토큰도 폐기 | `같은_리프레시_토큰으로_동시_재발급하면_1건만_성공한다` |

**순환 발급과 재사용 탐지 추가 검증**

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 묶음이 폐기된 뒤 같은 사용 완료 토큰이 또 옴 | 계속 재사용으로 탐지 | 두 번째도 401 `REFRESH_TOKEN_REUSE_DETECTED`, 처음 폐기 시각 유지 | `묶음이_폐기된_뒤에도_사용_완료_토큰은_재사용으로_탐지한다` |
| 재사용 탐지 (테스트 트랜잭션 없음) | 폐기가 401과 함께 커밋 | 새 트랜잭션에서 묶음 전체 폐기 확인, 최신 토큰 401 | `재사용_탐지의_묶음_폐기는_커밋된다` |
| 사용 완료된 이전 토큰으로 로그아웃한 뒤 최신 토큰 A로 재발급 | A도 폐기 | 로그아웃 200, A는 401 `REFRESH_TOKEN_INVALID`, 묶음 2행 모두 폐기 | `사용_완료된_이전_토큰으로_로그아웃해도_최신_토큰까지_폐기된다` |
| 최신 토큰으로 로그아웃 | 묶음 전체 폐기 | 두 번 재발급한 묶음 3행 모두 폐기 | `로그아웃하면_같은_로그인의_토큰_묶음이_모두_폐기된다` |
| 한 로그인에서 로그아웃 | 다른 로그인의 묶음 유지 | 다른 묶음 미폐기, 재발급 200 | `로그아웃은_다른_로그인의_묶음을_폐기하지_않는다` |
| 로그아웃 (테스트 트랜잭션 없음) | 폐기가 커밋 | 새 트랜잭션에서 묶음 전체 폐기 확인 | `로그아웃의_묶음_폐기는_커밋된다` |
| 다른 요청이 같은 토큰의 잠금을 쥐고 놓지 않음 | 500이 아니라 401, 토큰 상태 불변 MySQL 잠금 대기 한도(3초, 실측 3.5초) 뒤 401 `REFRESH_TOKEN_INVALID`, `used_at` 그대로, 잠금이 풀린 뒤 같은 토큰으로 재발급 200 | `잠금_대기_시간이_초과되면_401` |

**테스트가 실제로 버그를 잡는지 확인한 실험** (확인 후 원래대로 되돌렸습니다)

| 일부러 넣은 버그 | 결과 |
|---|---|
| 잠금 조회에서 `FOR UPDATE` 제거 | 동시 10건 중 7건 성공 → 동시 재발급 테스트 실패 |
| `noRollbackFor` 제거 | 트랜잭션 없는 테스트 2개 실패(묶음 미폐기). 테스트 트랜잭션 안에서 도는 시나리오 테스트는 모두 통과해, 이 버그를 잡지 못함을 확인 |
| 확인 순서를 "폐기 → 사용 완료"로 되돌림 | 동시 10건 중 두 번째 요청만 재사용 탐지, 나머지 8건은 일반 거부 → 동시 재발급 테스트와 순서 검증 테스트 실패 |

**세션 5 과제 시나리오**

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 로그인 성공 | 액세스 토큰과 리프레시 토큰 둘 다 발급 | 200, 액세스 토큰의 sub가 가입한 사용자, 리프레시 토큰 43자, `refreshTokenExpiresIn` 1209600 | `로그인하면_액세스_토큰과_리프레시_토큰을_함께_발급한다` |
| 유효한 리프레시 토큰으로 재발급 | 새 액세스 토큰 발급, 그 토큰으로 보호 API 정상 호출 | 200, 새 토큰의 사용자·권한 일치, 보호 API 200 | `유효한_리프레시_토큰으로_재발급한_토큰으로_보호_API를_호출할_수_있다` |
| 만료된 리프레시 토큰으로 재발급 | 401 + 오류 코드 | 401 `REFRESH_TOKEN_INVALID` | `만료된_리프레시_토큰이면_401_REFRESH_TOKEN_INVALID` |
| 존재하지 않거나 임의로 만든 리프레시 토큰으로 재발급 | 401 + 오류 코드 | 발급된 적 없는 43자 토큰과 임의 문자열 모두 401 `REFRESH_TOKEN_INVALID` | `존재하지_않거나_임의로_만든_리프레시_토큰이면_401_REFRESH_TOKEN_INVALID` |
| 로그아웃한 리프레시 토큰으로 재발급 | 401 + 오류 코드 | 401 `REFRESH_TOKEN_INVALID`, 미발급 토큰의 거부 응답과 본문 동일 | `로그아웃한_리프레시_토큰으로_재발급하면_401_REFRESH_TOKEN_INVALID` |
| 리프레시 토큰을 Authorization 헤더에 넣어 보호 API 호출 | 401 | 401 `TOKEN_INVALID` | `리프레시_토큰을_Authorization_헤더에_넣으면_401_TOKEN_INVALID` |
| 액세스 토큰을 재발급 API 본문에 넣어 호출 | 401 | 401 `REFRESH_TOKEN_INVALID` | `액세스_토큰을_재발급_본문에_넣으면_401_REFRESH_TOKEN_INVALID` |
| 로그아웃 후 DB 확인 | 해당 리프레시 토큰이 폐기 상태 | 1차 캐시를 비운 뒤 재조회해 `revoked_at` 기록 확인 | `로그아웃하면_DB의_리프레시_토큰이_폐기_상태가_된다` |
| DB에 저장된 값 확인 | 원문이 아니라 해시 | `token_hash`가 원문의 SHA-256과 같고 원문과 다름, 원문으로는 조회되지 않음 | `DB에는_리프레시_토큰_원문이_아니라_해시가_저장된다` |

**세션 5 추가 검증**

| 상황 | 기대 결과 | 실제 결과 | 테스트 메서드명 |
|---|---|---|---|
| 로그인 실패 | 리프레시 토큰 미저장 | 401 `LOGIN_FAILED`, `data` 없음, DB 행 없음 | `로그인에_실패하면_리프레시_토큰을_저장하지_않는다` |
| 같은 사용자가 두 번 로그인 | 기기별 토큰 | 서로 다른 토큰 2개, 둘 다 유효 | `로그인할_때마다_새_리프레시_토큰이_생긴다` |
| 만료된 액세스 토큰을 헤더에 단 채 재발급 | 막히지 않음 | 200 | `만료된_액세스_토큰을_헤더에_단_채로도_재발급할_수_있다` |
| 재발급 토큰의 권한 | DB의 사용자 기준 | 로그인 없이 저장한 관리자 토큰으로 재발급 → role `ADMIN`, 관리자 API 200 | `재발급한_액세스_토큰의_권한은_DB의_사용자에서_정해진다` |
| 만료·미발급·액세스 토큰으로 재발급 | 같은 거부 응답 | 세 응답 본문 문자열이 모두 동일 | `재발급_거부_응답은_원인과_관계없이_같다` |
| 발급 길이(43자)와 다른 리프레시 토큰으로 재발급·로그아웃 | DB 조회 없이 거부 | 재발급 401 `REFRESH_TOKEN_INVALID`(다른 거부와 본문 동일), 로그아웃 200, 쿼리 0건 | `길이가_다른_리프레시_토큰은_DB_조회_없이_401`, `길이가_다른_리프레시_토큰으로_로그아웃하면_DB_조회_없이_200` |
| 재발급 요청의 리프레시 토큰이 비거나 빠짐 | 400 | 400 `INVALID_INPUT_VALUE` | `리프레시_토큰이_비어_있으면_400` |
| 한 기기에서 로그아웃 | 다른 기기 유지 | 로그아웃한 토큰 401, 다른 기기 토큰 200 | `로그아웃해도_다른_기기의_리프레시_토큰은_유지된다` |
| 없는 토큰·이미 로그아웃한 토큰으로 로그아웃 | 200, 폐기 시각 유지 | 200, 두 번째 로그아웃 뒤에도 처음 폐기 시각 그대로 | `로그아웃은_멱등이다` |
| 로그아웃 요청의 리프레시 토큰이 비어 있음 | 400 | 400 `INVALID_INPUT_VALUE` | `로그아웃_리프레시_토큰이_비어_있으면_400` |
| 로그아웃 후 기존 액세스 토큰으로 보호 API | 만료 전까지 유효 (한계) | 200 | `로그아웃_후에도_액세스_토큰은_만료_전까지_유효하다` |
| 같은 사용자가 다시 로그인 | 만료된 행만 정리 | 만료 행 3개(순환한 묶음 포함) 삭제, 유효 토큰과 새 토큰 2개만 남음, 지워진 토큰으로 재발급 401 `REFRESH_TOKEN_INVALID` | `로그인하면_같은_사용자의_만료된_토큰_행이_지워진다` |
| 다른 사용자가 로그인 | 내 행은 그대로 | 다른 사용자의 만료 행 1개 유지 | `로그인해도_다른_사용자의_만료된_토큰_행은_남는다` |
| 같은 사용자의 발급 10건이 동시에 (만료 행 0개 / 3개) | 교착 없이 모두 성공 | 두 경우 모두 10건 성공, 남은 행 10개 모두 유효 (MySQL) | `같은_사용자가_동시에_발급해도_교착이_없다` |

이 밖에 단위 테스트로 토큰 생성기(길이, 점 없음, 1만 개 중복 없음, SHA-256 고정값), 엔티티 상태(만료 경계, 순환 시 사용 완료 표시, 새 토큰의 묶음·만료 시각 상속), 저장소(해시 잠금 조회, 해시 유니크 제약)를 확인합니다. `./gradlew test` 전체 281개가 통과했습니다.

## 테스트 실행 환경

대부분의 테스트는 H2(MySQL 모드)로 돌고, **동시성 테스트는 Testcontainers로 띄운 MySQL 8.0 컨테이너에서 돕니다.**
`*ConcurrencyTest` 8개(좌석, 예매 결제, 예매 취소, 매점 승인, 매점 환불, 찜, 회원가입, 리프레시 토큰)와 로컬 샘플 데이터 검증(`LocalSampleDataTest`)이 대상입니다.

- **왜 나눴나요:** 잠금 대기, 교착, 간격 잠금(gap lock)은 DB 엔진마다 동작이 다르고, H2의 MySQL 모드는 문법만 맞춰 줄 뿐 InnoDB의 잠금 규칙을 흉내 내지 않습니다. 실제로 리프레시 토큰 정리의 교착은 H2에서는 재현되지 않고 MySQL에서만 났습니다. 나머지 테스트는 잠금과 무관해 빠른 H2를 유지합니다.
- **구성:** `support/MySqlContainerConfig`가 컨테이너를 `@ServiceConnection` 빈으로 등록하고, 동시성 테스트가 `@Import`로 가져옵니다. 컨테이너가 빈이라 설정이 같은 클래스끼리 Spring 컨텍스트와 컨테이너를 공유합니다. 토스 클라이언트를 목으로 바꾸는 결제·환불 테스트는 컨텍스트가 따로라, 전체 실행에서 컨테이너는 두 번 뜹니다. 같은 설정이 경합 순서를 고정하는 게이트(`RaceGateConfig`, 「동시성 문제 — 재현 방법」)도 함께 가져옵니다. 잠금 대기 한도는 운영 설정과 같은 3초(`--innodb-lock-wait-timeout=3`)입니다.
- **필요한 것:** Docker가 실행 중이어야 합니다. Docker가 없으면 이 테스트들은 건너뛰지 않고 **실패**합니다. 동시성 검증이 조용히 빠지는 것을 막기 위해서입니다. 처음 실행할 때는 `mysql:8.0` 이미지를 받느라 시간이 더 걸립니다.

## 4주차 리팩토링

객체지향 원칙(SOLID, 캡슐화)과 KISS·YAGNI·DRY를 기준으로 기존 코드를 다시 읽고, 문제가 분명한 10곳을 고쳤습니다. 모두 동작을 바꾸지 않는 리팩토링이라 API 요청·응답 형식은 그대로이고, 단계마다 전체 테스트 287개가 통과하는 것을 확인했습니다.

| # | 대상 | 원칙 |
|---|---|---|
| 1 | 선점 만료 판정 규칙을 한 곳으로 | DRY |
| 2 | 결제 시점 가격 복사를 엔티티가 책임 | 캡슐화 |
| 3 | 엔티티 존재 검증을 Repository로 | DRY |
| 4 | 예매 생성 메서드 책임 분리 | SRP |
| 5 | 매점 구매 메서드 책임 분리 | SRP |
| 6 | 구매 시각을 주입된 Clock 기준으로 | DIP |
| 7 | 회차 검색의 시간 범위 계산 분리 | SRP |
| 8 | 내역 행 묶기 단순화 | KISS |
| 9 | 입력값 오류 응답 생성 중복 제거 | DRY |
| 10 | 로그인·재발급 토큰 응답 DTO 통합 | DRY |

### 1. 선점 만료 판정 규칙을 한 곳으로 — DRY

"결제 대기(`PENDING`)이고 만료 시각이 지났으면 만료"라는 규칙이 두 곳에 따로 있었습니다. 결제할 때 쓰는 `Reservation.isExpired()`와 조회 응답을 만드는 `ReservationResponse.resolveStatus()`입니다. 한쪽 기준만 바뀌면 결제는 만료로 거절되는데 조회 화면에는 "결제대기"로 보이는 식으로 두 경로가 어긋납니다.

중복이 생긴 이유는 조회 경로가 엔티티 없이 프로젝션(스칼라 행)만 받아서 엔티티 메서드를 부를 수 없었기 때문입니다. 그래서 규칙을 엔티티가 아니라 **상태 enum**으로 옮겨, 엔티티와 DTO가 같은 메서드를 쓰게 했습니다.

```java
// ReservationStatus
public boolean isHoldExpired(LocalDateTime expiresAt, LocalDateTime now) {
    return this == PENDING && !now.isBefore(expiresAt);
}

public ReservationStatus resolve(LocalDateTime expiresAt, LocalDateTime now) {
    return isHoldExpired(expiresAt, now) ? EXPIRED : this;
}
```

### 2. 결제 시점 가격 복사를 엔티티가 책임 — 캡슐화

```java
// 전
reservation.addSeat(row, col, audienceType, screening.getPrice());
purchase.addItem(product, quantity, product.getPrice());

// 후
reservation.addSeat(row, col, audienceType);
purchase.addItem(product, quantity);
```

"결제 시점 가격을 복사 저장한다"는 도메인 규칙인데, 서비스가 가격을 꺼내 엔티티에 다시 넣어 주고 있었습니다. 예매는 이미 회차를, 구매는 상품을 들고 있으므로 가격을 따로 받을 이유가 없습니다. 가격을 인자로 열어 두면 호출하는 쪽이 회차 가격과 다른 값을 넣어도 막을 방법이 없습니다.

이제 가격은 엔티티 안에서만 정해집니다. `addSeat()`가 회차를 부모 예매에서 가져와 부모-자식 불일치를 막던 기존 방식과 같은 이유입니다.

### 3. 엔티티 존재 검증을 Repository로 — DRY

`findById(id).orElseThrow(() -> new CustomException(USER_NOT_FOUND))`와 `if (!existsById(id)) throw ...`가 서비스 7개에 14번 반복됐습니다. "사용자가 없으면 어떤 오류로 알리는가"라는 같은 지식이 흩어져 있어, 오류 코드를 바꾸려면 14곳을 모두 찾아야 했습니다.

`UserRepository`, `BranchRepository`, `MovieRepository`에 default 메서드 `getByIdOrThrow(id)`, `validateExists(id)`를 두었습니다.

```java
// 전
User user = userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

// 후
User user = userRepository.getByIdOrThrow(userId);
```

- **서비스가 아니라 Repository에 둔 이유:** 이 프로젝트는 다른 도메인의 service를 참조하지 않고 repository만 참조합니다. 예매·매점 서비스도 사용자와 지점을 찾아야 하므로, 모든 도메인이 함께 쓸 수 있는 자리는 Repository뿐입니다.
- **쓰는 곳이 없는 메서드는 만들지 않았습니다.** 영화는 존재 여부만 확인하는 곳이 없어 `MovieRepository`에는 `validateExists`를 두지 않았습니다(YAGNI).
- **테스트:** Mockito mock은 default 메서드까지 가짜로 만들어 null을 돌려줍니다. 서비스 단위 테스트의 `UserRepository` mock에 `Answers.CALLS_REAL_METHODS`를 붙여 default 메서드는 실제 코드대로 돌고, 기존 `findById` stub이 그대로 쓰이게 했습니다.

### 4. 예매 생성 메서드 책임 분리 — SRP

`ReservationService.create()`는 주석으로 1~7번 단계를 나눈 60줄짜리 메서드였습니다. 회차·사용자 조회, 좌석 범위 검증, 요청 안의 중복 검증, 만료 선점 정리, 점유 확인, 생성, 저장 예외 번역을 혼자 했습니다. 주석으로 단계를 나눠야 읽힌다는 것 자체가 한 메서드가 여러 책임을 지고 있다는 신호입니다.

```java
public ReservationResponse create(Long userId, ReservationCreateRequest req) {
    // 회차·사용자 조회
    validateSeats(screening.getTheater().getTheaterType(), req.seats());
    releaseExpiredHolds(screening.getId(), req.seats(), now);
    ensureNotOccupied(screening.getId(), req.seats(), now);
    // 예매 생성 + 좌석 추가
    return ReservationResponse.from(saveHold(reservation), now);
}
```

| 메서드 | 책임 |
|---|---|
| `validateSeats()` | 좌석 범위 + 요청 안의 중복 좌석 |
| `releaseExpiredHolds()` | 요청한 좌석을 막고 있는 만료 선점 해제 |
| `ensureNotOccupied()` | 이미 점유된 좌석 사전 확인 |
| `saveHold()` | 저장 + DB 예외를 오류 코드로 번역 (유니크 위반 → `SEAT_ALREADY_RESERVED`, 락 경합 → `SEAT_RESERVATION_CONFLICT`) |

이제 `create()`는 흐름만 보여주고, 각 단계의 세부와 이유(검증 순서, 데드락 방지 정렬, 예외 번역 기준)는 해당 메서드의 주석에 있습니다.

같은 클래스 안에서 좌석 좌표를 키로 만드는 방식이 두 가지였던 것도 고쳤습니다. 점유 확인은 `"2:3"` 문자열을, 만료 선점 조회는 `2 * 100 + 3` 정수를 썼습니다. `ReservationSeat.key(row, col)` 하나로 통일하고 좌석 라벨 규칙(`label()`) 옆에 두어, 좌석 좌표 규칙을 한 곳에 모았습니다. 요청 안의 중복 검사도 이 키로 하므로, 키가 겹치지 않는 조건(열 100 미만)을 보장하는 범위 검사를 반드시 먼저 합니다.

### 5. 매점 구매 메서드 책임 분리 — SRP

- **이름이 하는 일을 숨기고 있었습니다.** `sortedByProductId()`는 이름은 "정렬"인데 중복 상품 검증까지 했습니다. 호출부만 읽어서는 여기서 검증이 일어난다는 것을 알 수 없습니다. 검증(`validateNoDuplicateProducts()`)과 정렬을 나눴습니다.
- **정렬은 재고 차감 안으로 옮겼습니다.** 상품 id 순으로 정렬하는 이유가 재고 락을 잡는 순서를 고정해 데드락을 막는 것이므로, 락을 잡는 코드 옆이 맞는 자리입니다. 재고 락·차감·항목 추가·락 타임아웃 번역은 `deductStocks()`로, 지점 조회와 운영 여부 확인은 `getOperatingBranch()`로 뺐습니다.
- **운영 판정 메서드 이름을 바꿨습니다.** `Branch.isReservable()` → `isOperating()`. 매점 구매가 "예매 가능한가"를 묻는 것은 의미가 맞지 않고, 실패 코드도 `BRANCH_NOT_OPERATING`이었습니다.

검증 순서는 그대로라, 없는 지점과 중복 상품이 함께 와도 전처럼 중복 상품 400이 먼저 납니다.

### 6. 구매 시각을 주입된 Clock 기준으로 — DIP

다른 코드는 모두 주입받은 `Clock`으로 현재 시각을 정하는데, `Purchase` 생성자만 `LocalDateTime.now()`로 시스템 시계를 직접 읽었습니다. 엔티티가 구체적인 시계에 의존하니 테스트에서 구매 시각을 고정할 수 없고, 예매와 구매가 서로 다른 시계를 보게 됩니다.

예매(`Reservation.builder().now(now)`)처럼 시각을 빌더로 받도록 바꾸고, `PurchaseService`가 `Clock`을 주입받아 넘깁니다.

### 7. 회차 검색의 시간 범위 계산 분리 — SRP

`ScreeningService.search()`가 조회 시간 범위 계산과 조회·그룹핑을 함께 했습니다. 범위 계산은 "날짜를 안 고르면 오늘 → 시간대를 안 고르면 하루 전체 → 이미 시작한 회차는 예매할 수 없으니 시작을 지금으로 당기기"의 세 단계입니다.

계산을 `searchWindow()`로 빼고 결과를 `SearchWindow(startInclusive, endExclusive)` record로 받습니다. 지난 날짜를 조회할 때처럼 범위가 비는 경우(`!start.isBefore(end)`)는 `isEmpty()`라는 이름으로 드러냈습니다. 이 서비스 밖에서는 쓰지 않으므로 서비스 안의 private record로 두었습니다.

### 8. 내역 행 묶기 단순화 — KISS

예매·구매 내역은 "헤더 × 항목" 행으로 받아 id별로 묶습니다. `LinkedHashMap`을 만들어 `computeIfAbsent`로 채우는 반복문을 직접 짰는데, 표준 컬렉터 한 번이면 되는 일입니다.

```java
// 전
Map<Long, List<PurchaseHistoryRow>> byPurchase = new LinkedHashMap<>();
for (PurchaseHistoryRow row : rows) {
    byPurchase.computeIfAbsent(row.purchaseId(), id -> new ArrayList<>()).add(row);
}
return byPurchase.values().stream()...

// 후
return rows.stream()
        .collect(Collectors.groupingBy(PurchaseHistoryRow::purchaseId, LinkedHashMap::new, Collectors.toList()))
        .values().stream()...
```

행 순서(최신순)를 지키는 `LinkedHashMap`은 그대로 씁니다. 첫 행 접근도 `get(0)`과 `getFirst()`가 섞여 있던 것을 `getFirst()`로 맞췄습니다.

### 9. 입력값 오류 응답 생성 중복 제거 — DRY

`GlobalExceptionHandler`의 세 핸들러(검증 실패, 타입 불일치, 필수 파라미터 누락)가 같은 400 응답 생성 코드와 `value != null ? value.toString() : null`을 각각 반복했습니다. 응답 생성은 `invalidInput(fieldErrors)`로, 값 변환은 `FieldError.of(field, Object value, reason)` 정적 팩토리로 모았습니다. `FieldError` 생성자는 private으로 바꿔 만드는 경로를 하나로 줄였습니다.

본문 파싱 실패 핸들러는 그대로 두었습니다. 이 오류는 필드 오류 목록 없이 응답하는데, 같은 메서드로 합치면 응답에 빈 `errors: []`가 새로 생겨 응답 형식이 바뀝니다.

### 10. 로그인·재발급 토큰 응답 DTO 통합 — DRY

`LoginResponse`와 `TokenReissueResponse`는 필드 5개, `BEARER` 상수, 팩토리 메서드까지 똑같았습니다. 둘 다 "새 토큰 한 쌍 발급"이라는 같은 일의 결과이므로 `TokenResponse` 하나로 합쳤습니다.

복사본이 실제로 어긋나기 시작한 상태였습니다. `refreshTokenExpiresIn` 설명이 한쪽은 "유효 시간", 다른 쪽은 "남은 유효 시간"이었는데, 실제 값은 둘 다 남은 시간입니다. 로그인 직후에는 남은 시간이 전체 유효기간과 같을 뿐입니다. 이제 설명은 한 곳에 한 번만 있고, 필드를 더할 때 한쪽을 빠뜨릴 일도 없습니다. JSON 응답 모양은 같아서 클라이언트에는 영향이 없고, Swagger의 스키마 이름만 바뀝니다.

## 결제

토스페이먼츠 결제위젯(테스트 키)으로 예매와 매점 구매에 결제를 붙였습니다. 이 절은 주문부터 승인·조회·취소까지의 흐름과 상태를 다룹니다. 같은 흐름 위에서 생기는 문제는 각자의 절에 있습니다. 동시 요청은 「동시성 문제」 6~10번, 같은 요청의 재전송은 「중복 결제와 멱등성」, 토스가 실패하거나 결과를 알 수 없을 때는 「외부 API 통신과 예외 처리」를 보면 됩니다. 토스 API 자체의 정리는 [`docs/toss-payments-guide.md`](docs/toss-payments-guide.md)에, 세션별 결정은 [`docs/refactoring-log.md`](docs/refactoring-log.md)의 결제 세션 1~5에 있습니다.

### 결제가 필요한 기능 — 예매와 매점

돈을 받는 곳은 영화 예매와 매점 구매 두 곳입니다. 결제 순서는 같지만, **무엇을 언제 붙잡아 두는지**가 달라 흐름이 갈립니다.

| | 예매 | 매점 |
|---|---|---|
| 붙잡는 대상 | 특정 좌석. 다른 좌석으로 대신할 수 없습니다 | 수량. 누가 가져가든 개수만 맞으면 됩니다 |
| 붙잡는 시점 | **결제 전.** 좌석을 고르는 순간 선점(`PENDING`, 10분) | **승인 직전.** 승인 T1에서 재고 차감 |
| 승인이 거절되면 | 좌석을 유지하고, 선점 시간 안에 새 결제 주문으로 다시 결제 | 재고를 돌려놓고 주문도 실패로 끝냄. 다시 사려면 새 주문 |
| 주문 : 결제 | **1 : N** (예매 하나에 결제 시도 여럿) | **1 : 1** (`purchase_id` 유니크) |
| 결제 후 취소 | 회원이 좌석 단위로 (상영 20분 전까지) | 매장(관리자)이 수량 단위로 환불 |

좌석은 결제창에 들어가는 동안 남이 가져가면 안 되므로 미리 잡아야 합니다. 상품은 대신할 수 있는 수량이라 미리 잡을 이유가 없습니다. 주문만 만들고 결제창을 닫은 회원이 재고를 붙잡는 일도 막을 수 있습니다.

### 전체 주문·결제 흐름

```
[예매]
POST /api/reservations                        좌석 선점 → 예매 PENDING (10분)
POST /api/reservations/{id}/payments          결제 주문 생성 → Payment READY, {orderId, orderName, amount} 응답
   ── 브라우저: 결제위젯 requestPayment(orderId, amount) → 카드사·간편결제 인증
   ── 토스가 successUrl?paymentKey=..&orderId=..&amount=.. 로 되돌려 보냄
POST /api/reservations/{id}/payments/confirm  {paymentKey, orderId, amount}
   T1  검증 → 선점 연장 → Payment IN_PROGRESS (paymentKey 저장)   ← 커밋
   ──  토스 승인 API (트랜잭션 밖)
   T2  승인: Payment DONE + 예매 RESERVED / 거절: Payment FAILED + 연장 되돌림

[매점]
POST /api/purchases                           주문 생성 → 구매 PENDING(단가 복사) + Payment READY. 재고는 그대로
   ── 브라우저: 결제위젯 인증 → successUrl
POST /api/purchases/{id}/payments/confirm     {paymentKey, orderId, amount}
   T1  검증 → Payment IN_PROGRESS → 재고 차감                     ← 커밋
   ──  토스 승인 API (트랜잭션 밖)
   T2  승인: Payment DONE + 구매 PAID / 거절: 재고 복구 + Payment·구매 FAILED
```

**주문은 서버가 먼저 만듭니다.** 결제위젯은 브라우저에서 돌기 때문에 금액을 브라우저가 정하게 두면 회원이 바꿀 수 있습니다. 서버가 좌석 가격 합(또는 상품 단가 × 수량)으로 금액을 계산해 `Payment`에 저장하고, 그 행의 `orderId`와 `amount`만 결제위젯에 넘깁니다. 승인할 때 기준이 되는 값은 이 저장된 금액입니다.

**`orderId`는 `cgv-r-`(예매) / `cgv-p-`(매점) + UUID 32자입니다.** 예매 id를 그대로 쓰지 않은 이유는 셋입니다. 토스 규칙(6~64자)보다 짧아지고, 결제를 다시 시도할 때마다 새 값이어야 하며(토스는 `orderId` 재사용을 막습니다), 순차 값이면 남의 주문번호를 짐작할 수 있습니다.

**클라이언트 키는 서버가 내려줍니다.** `GET /api/payments/widget-config`는 결제위젯 초기화에 필요한 클라이언트 키만 응답합니다. 시크릿 키는 서버의 `TossPaymentsClient`만 씁니다. 두 키는 `.env`의 환경변수로 넣습니다(「비밀값 로컬 설정」). 브라우저에서 직접 확인할 수 있게 테스트 페이지(`/payment-test.html`)와 local 프로필 전용 샘플 데이터를 두었습니다.

### 결제 정보 저장 구조

| 엔티티 | 단위 | 담는 것 | 이렇게 나눈 이유 |
|---|---|---|---|
| `Payment` | **결제 시도 1건 = 1행** | `orderId`, 금액, 상태, `paymentKey`, 결제수단, 승인 시작·승인 시각, 누적 취소액(`canceledAmount`), 실패 코드·메시지 | 예매에 컬럼을 붙이면 거절 뒤 재결제를 담을 수 없습니다. 토스 값이 예매·구매 엔티티에 섞이지도 않습니다 |
| `PaymentCancel` | **취소 요청 1건 = 1행** | 금액, 사유, 상태, 멱등키, 요청 시각, 토스 `transactionKey`, 취소 시각 | 누적액만 두면 진행 중인 취소, 멱등키, 어느 좌석·수량을 취소한 것인지 담을 곳이 없습니다. 결과 불명 취소를 정리하려면 이 값들이 필요합니다 |
| `ReservationSeat.paymentCancel` | 좌석 → 취소 요청 FK | 이 좌석을 어느 취소로 풀었는지 | 좌석은 한 번만 취소되므로 FK 하나면 됩니다 |
| `PurchaseProductCancel` | 취소 요청 × 상품 라인 × 수량 | 이 환불에서 몇 개를 돌려줬는지 | 한 상품 라인이 여러 번 나눠 환불될 수 있습니다 |

`Payment`는 예매와 구매 중 **정확히 하나**에 속합니다. 생성자가 먼저 막고, DB의 CHECK 제약(`ck_payment_one_target`)이 엔티티를 거치지 않는 쓰기까지 막습니다. `canceledAmount`는 잔액 검사를 행 합산 없이 하려고 둔 캐시입니다. 그래서 `PaymentCancel.complete()`를 거쳐서만 늘어나게 해, 이력 없이 누적액만 바뀌는 일이 없게 했습니다.

### 결제 상태와 상태 전이

```
Payment
  READY ──승인 시작(T1)──▶ IN_PROGRESS ──토스 승인──▶ DONE ──부분 취소──▶ PARTIAL_CANCELED ──잔액 0──▶ CANCELED
    │                         │                       └──────────전액 취소(잔액 0)──────────────────────▶ CANCELED
    │                         └──토스 거절 / 토스에 닿지 못함──▶ FAILED
    └── 결제창 이탈: READY로 남음 (예매는 선점 만료, 매점은 재고를 잡지 않아 피해 없음)

PaymentCancel
  IN_PROGRESS ──토스 취소 성공──▶ DONE
              └─토스 거절──────▶ FAILED

예매  PENDING ──승인──▶ RESERVED ──남은 좌석 모두 취소──▶ CANCELLED
         ├── 선점 만료 ──▶ EXPIRED
         └── 결제 전 취소 ──▶ CANCELLED
구매  PENDING ──승인──▶ PAID ──남은 수량 모두 환불──▶ CANCELLED
         └── 거절 ──▶ FAILED
```

| 상태 | 뜻 | 왜 이렇게 두었나 |
|---|---|---|
| `READY` | 주문만 만들어짐 | 승인은 `READY`에서만 시작합니다. 거절·승인된 주문으로 다시 승인할 수 없고, 다시 결제하려면 새 주문을 만듭니다 |
| `IN_PROGRESS` | 토스를 부르는 중 **또는** 토스 결과를 모름 | 두 경우 모두 확정해서도 실패로 처리해서도 안 된다는 점이 같습니다. 이 상태가 있는 동안 같은 예매의 새 결제와 취소를 막습니다. 결과를 모르는 결제는 승인 시작 2분 뒤 스케줄러가 토스에 물어 정리합니다 |
| `DONE` | 승인됨 | 결제수단·승인 시각은 토스 응답에서 저장합니다 |
| `FAILED` | 거절됨 | 토스 오류 코드·메시지를 남깁니다. 승인 시작 때 저장한 `paymentKey`는 지웁니다. 토스가 확인하기 전의 값이라, 잘못된 키가 남아 있으면 유니크 제약 때문에 그 키의 진짜 주문이 승인을 시작하지 못합니다 |
| `PARTIAL_CANCELED` / `CANCELED` | 일부 / 전액 환불 | **잔액이 0이면 `CANCELED`입니다.** 토스는 부분 취소를 거듭해 잔액이 0이 돼도 `PARTIAL_CANCELED`로 두지만(실제 토스로 확인), 회원에게 중요한 사실은 "더 돌려받을 돈이 남았나"이고 그건 잔액이 정합니다 |

**취소 진행 상태는 `Payment`가 아니라 `PaymentCancel`이 갖습니다.** `Payment`에 `IN_PROGRESS`를 함께 쓰면 "승인 결과를 모름"과 "취소 결과를 모름"을 구분할 수 없고, 부분 취소가 진행 중인 동안 결제의 원래 상태(`DONE`인지 `PARTIAL_CANCELED`인지)도 잃습니다.

### 결제 승인 과정 — T1 → 토스 → T2

토스 승인 응답은 최대 60초까지 걸릴 수 있습니다. 승인 전체를 한 트랜잭션으로 감싸면 그동안 예매·재고 행의 락과 DB 커넥션을 쥐고 있게 되고, 토스가 승인한 뒤 커밋이 실패하면 승인 흔적까지 롤백됩니다. 그래서 세 단계로 나눴습니다(이렇게 나누지 않았을 때 생기는 문제의 재현은 「동시성 문제」 8번).

| 단계 | 트랜잭션 | 예매 (`ReservationService`) | 매점 (`PurchaseService`) |
|---|---|---|---|
| **T1** `startPayment` | O | 예매 행 잠금 → 아래 「승인 전 서버 검증」 → 선점을 3분 연장 → `READY → IN_PROGRESS` + `paymentKey`·승인 시작 시각 저장 | 구매 행 잠금 → 검증 → `READY → IN_PROGRESS` → 재고 차감(상품 id 순서로 잠금) |
| **토스 호출** | **X** | `TossPaymentsClient.confirm`, 멱등키 = `orderId` | 같음 |
| **T2 승인** `completePayment` | O | 결제 `DONE` + 결제수단·승인 시각, 예매 `RESERVED` | 결제 `DONE`, 구매 `PAID` + 구매 시각 |
| **T2 거절** `failPayment` | O | 결제 `FAILED`, 늘린 선점 시간을 되돌림. 좌석은 유지 | 결제·구매 `FAILED`, 재고 복구 |

순서를 잇는 `ReservationPaymentService` / `PurchasePaymentService`에는 `@Transactional`을 붙이지 않았습니다. 붙이면 T1·T2가 바깥 트랜잭션에 합류해, 나눈 의미가 사라지고 토스를 기다리는 동안 락을 쥡니다. 이 클래스들이 토스 결과에 따라 T2를 고릅니다.

| 토스 결과 | 결제 | 회원 응답 |
|---|---|---|
| 승인 | `DONE` | 200, 확정된 예매·구매 |
| 거절 (카드 한도 초과 등) | `FAILED` | 402 `PAYMENT_REJECTED` — 다른 결제수단으로 다시 |
| 토스가 판단하지 않은 거절 (키 오류, 429, 연결 실패) | `FAILED` | 503 `PAYMENT_SERVICE_UNAVAILABLE` — 잠시 후 다시 |
| 결과 모름 (타임아웃, 5xx, 해석할 수 없는 응답) | `IN_PROGRESS` 유지 | 504 `PAYMENT_RESULT_UNKNOWN` — 내역에서 확인 |
| 토스는 승인했는데 T2가 실패 | `IN_PROGRESS` 유지 (T2 롤백) | 504 `PAYMENT_RESULT_UNKNOWN` + `orderId`·`paymentKey` error 로그 |

**결과를 모르는 결제는 실패로 처리하지 않습니다.** 실패로 처리하면 이미 돈이 빠진 회원이 다시 결제해 이중 결제가 됩니다. 결제를 `IN_PROGRESS`로 두면, 승인 시작 2분 뒤 스케줄러(또는 관리자 API)가 T1에서 저장한 `paymentKey`로 토스를 조회해 결과를 맞춥니다. 그사이 선점이 만료돼 좌석을 잃은 승인 결제는 자동으로 환불합니다. 정리 규칙은 「외부 API 통신과 예외 처리」의 2번에 있습니다.

### 승인 전 서버 검증

successUrl로 돌아오는 `paymentKey`·`orderId`·`amount`는 주소창 값입니다. 회원이 얼마든지 바꿔서 승인 API를 부를 수 있으므로, **토스를 부르기 전에 T1에서 아래를 모두 확인합니다.** T1에서 예외가 나면 토스를 부르지 않으므로 돈이 빠지지 않습니다.

| # | 검증 | 이유 | 실패 응답 |
|---|---|---|---|
| 1 | 예매·구매가 **인증된 회원의 것인지** (행 잠금 조회의 조건) | 요청 본문의 회원 id는 믿지 않고 토큰의 회원 id로만 찾습니다. 남의 예매는 존재 여부도 알리지 않습니다 | 404 `RESERVATION_NOT_FOUND` / `PURCHASE_NOT_FOUND` |
| 2 | `orderId`가 **이 예매·구매의 주문인지** (`findByOrderIdAndReservationId`) | 다른 예매의 싼 주문번호로 비싼 예매를 확정하는 것을 막습니다 | 404 `PAYMENT_NOT_FOUND` |
| 3 | **`amount`가 서버에 저장한 금액과 같은지** | 기준은 늘 서버가 주문을 만들 때 저장한 금액입니다. 다르면 토스를 부르지도 않습니다. 브라우저 테스트 페이지의 "금액 조작" 체크로 실제 확인했습니다 | 400 `PAYMENT_AMOUNT_MISMATCH` |
| 4 | 같은 예매에 **승인 중인 다른 결제 시도가 없는지** | 두 탭에서 각각 인증을 마치면 주문번호가 달라 토스 멱등키로는 걸러지지 않고 두 번 결제됩니다(「동시성 문제」 7번) | 409 `PAYMENT_IN_PROGRESS` |
| 5 | 예매가 **아직 `PENDING`이고 선점이 살아 있는지** / 구매가 `PENDING`인지 | 확정된 예매나 만료된 선점에 돈을 받으면 안 됩니다. 이미 결제가 끝난 주문의 재요청도 여기서 걸립니다 | 409 `RESERVATION_NOT_PENDING` · `RESERVATION_EXPIRED` / `PURCHASE_NOT_PENDING` |
| 6 | 결제가 **`READY`인지** | 거절된 주문을 다시 승인하지 않습니다. 같은 주문의 중복 요청은 `IN_PROGRESS`에 걸립니다 | 409 `INVALID_PAYMENT_STATUS` / `PAYMENT_IN_PROGRESS` |
| 7 | (예매) 선점 연장 | 선점 만료 직전에 승인을 시작하면 토스를 기다리는 사이 다른 회원이 좌석을 가져갑니다(「동시성 문제」 6번). `max(만료 시각, 지금 + 3분)`으로 늘립니다 | — |
| 8 | (매점) 지점이 운영 중인지, 재고가 남았는지 | 주문 뒤에 휴관하거나 품절될 수 있습니다. 승인 직전에 걸러야 돈이 빠지지 않습니다 | 409 `BRANCH_NOT_OPERATING` / `OUT_OF_STOCK` |

**이미 결제가 끝난 주문에 다시 승인 요청이 오면** 5번에서 걸립니다. 예매는 `RESERVATION_NOT_PENDING`, 매점은 `PURCHASE_NOT_PENDING`으로 409를 주고, 토스는 다시 부르지 않습니다(`ReservationControllerTest.확정된_예매를_다시_승인하면_409이고_토스는_한_번만_불린다`). 거절된 주문을 같은 `orderId`로 다시 보내는 경우, 같은 요청이 동시에 두 번 오는 경우는 「중복 결제와 멱등성」의 표에 정리했습니다.

검증 순서에도 이유가 있습니다. **행 잠금 조회가 트랜잭션의 첫 쿼리입니다.** MySQL REPEATABLE READ는 첫 일반 SELECT 시점의 스냅샷을 쓰므로, 잠금보다 먼저 일반 SELECT를 하면 앞 요청이 커밋한 `IN_PROGRESS`를 잠금을 얻은 뒤에도 보지 못합니다. 매점은 `IN_PROGRESS` 전환을 재고 차감보다 먼저 합니다. 이미 승인 중인 주문의 중복 요청이 재고 행을 잠갔다가 롤백하는 일이 없게 하려는 것입니다.

### 결제 조회 — 우리 DB와 토스의 역할

| API | 보는 곳 | 대상 |
|---|---|---|
| `GET /api/reservations/{id}/payments` | 우리 DB | 회원. 예매의 결제 시도 전부(거절 포함, 결제창만 열고 떠난 `READY`는 제외)와 각 결제의 취소 이력 |
| `GET /api/purchases/{id}/payment` | 우리 DB | 회원. 구매의 결제 하나와 환불 이력 |
| `GET /api/admin/payments/{orderId}/toss` | 우리 DB + **토스** | 관리자. 같은 결제를 양쪽에서 읽어 나란히 보여주는 대조용 |

**회원 조회는 토스를 부르지 않습니다**(`ReservationControllerTest.결제_조회는_토스를_부르지_않는다`). 조회할 때마다 토스를 부르면 생기는 문제는 셋입니다.
- 호출 한도를 회원의 새로고침이 써 버립니다. 문서 공용 테스트 키는 여러 사람이 함께 쓰는 상점이라 한도(분당 100건)도 함께 씁니다.
- 응답이 토스만큼 느려집니다.
- 토스 장애가 우리 조회 장애로 번집니다.

토스에 묻는 것은 우리 DB만으로는 답할 수 없을 때뿐입니다. 결과를 모르는 결제·취소를 정리할 때(스케줄러, 관리자 정리 API)와 관리자가 양쪽을 대조할 때입니다.

**두 저장소가 답하는 질문이 다릅니다.**

| | 우리 DB | 토스 |
|---|---|---|
| 답하는 질문 | 이 좌석·상품이 이 회원의 것인가, 얼마에 샀고 얼마가 남았나 | 돈이 실제로 어떻게 움직였나 |
| 저장하는 것 | `paymentKey`, `orderId`, 상태, 금액, 결제수단 이름, 승인 시각, 누적 취소액, 취소 요청별 금액·사유·시각·`transactionKey`·멱등키 | 카드 번호, 할부, 승인번호, 영수증, 정산, 매입 상태 등 |
| 저장하지 않는 이유 | — | 우리 서비스의 판단에 쓰이지 않습니다. 필요하면 `paymentKey`로 언제든 물을 수 있고, 카드 정보는 가지고 있지 않는 편이 안전합니다 |

`paymentKey`는 토스에 결제를 물어볼 수 있는 키라 반드시 저장합니다. 공용 키에서는 `orderId` 조회가 동작하지 않아(실제 토스로 확인), **승인을 시작하는 T1에서** 미리 저장합니다. 승인이 성공한 뒤에 저장하면 결과를 모르는 결제는 물어볼 방법이 없습니다. 두 저장소가 어긋나면 대조·정리는 토스의 상태 이름이 아니라 **잔액과 취소 이력**으로 비교합니다. 잔액 0의 표기가 서로 다르기 때문입니다(위 상태 표).

### 전체·부분 취소

#### 취소도 T1 → 토스 → T2. 다만 자원을 놓는 순서가 승인과 반대입니다

| | 승인 | 취소 |
|---|---|---|
| 피해야 할 일 | 돈은 빠졌는데 좌석·재고가 없음 | 좌석은 풀렸는데 환불이 안 됨 |
| 순서 | 좌석·재고를 **먼저** 확보하고 토스를 부름 | 토스 환불이 **확인된 뒤에** 좌석을 놓음 |

- **T1:** 예매·구매 행 잠금 → 상태·기한·대상 검사 → 서버가 취소 금액 계산 → `PaymentCancel(IN_PROGRESS)` 저장 → 취소할 좌석·수량을 이 요청에 묶음
- **토스 취소 API:** 트랜잭션 밖에서 부릅니다. 멱등키는 취소 요청마다 새 UUID입니다
- **T2 성공:** 취소 `DONE` → 결제 누적액 증가 → 좌석 해제 / 환불 수량 반영 → 남은 것이 없으면 예매·구매 `CANCELLED`
- **T2 거절:** 취소 `FAILED`. 좌석·결제는 그대로이고 다시 요청할 수 있습니다

좌석을 T1에서 놓지 않는 이유는, 놓는 순간 다른 회원이 가져갈 수 있고 그 뒤에 환불이 거절되면 되돌릴 방법이 없기 때문입니다. 환불을 기다리는 동안 그 좌석은 여전히 점유 중이라 다른 회원의 선택은 `SEAT_ALREADY_RESERVED`가 됩니다.

#### 금액과 사유는 서버가 만듭니다

- **취소 금액을 클라이언트에게서 받지 않습니다.** 예매는 취소할 좌석의 `paidPrice` 합, 매점은 `unitPrice × 수량`입니다. 결제 시점 가격을 복사해 두었으므로 그사이 가격이 바뀌어도 낸 금액만큼 돌려줍니다.
- **전액 취소에도 `cancelAmount`를 넣습니다.** 빼면 토스가 남은 잔액을 전부 취소합니다. 우리와 토스의 잔액이 어긋났을 때 엉뚱한 금액이 조용히 환불되는 셈입니다. 넣으면 그 경우 토스가 `NOT_CANCELABLE_AMOUNT`로 거절해 어긋남이 드러납니다.
- 사유(`cancelReason`)는 `예매 부분 취소: A1, A2`, `매장 전체 환불: 팝콘×2`처럼 서버가 만듭니다. 토스 제한 200자를 넘으면 자릅니다.

#### 여러 번의 부분 취소와 남은 금액

- 결제의 남은 금액은 `amount - canceledAmount`입니다. `canceledAmount`는 **확정된(`DONE`) 취소만** 더합니다. 거절되거나 진행 중인 취소는 잔액을 바꾸지 않습니다.
- 잔액 검사(`Payment.verifyCancelable`)는 **두 번** 합니다. 취소 요청을 만들 때(T1)와 확정할 때(T2)입니다. 두 요청이 같은 잔액을 보고 함께 통과하는 일은 행 잠금과 "결제당 진행 중 취소 1건" 규칙이 먼저 막지만(「동시성 문제」 9·10번), 확정할 때 한 번 더 막습니다(`PaymentCancelTest.확정_전에_다른_취소가_잔액을_줄였으면_확정도_막힌다`).
- 응답의 `totalPrice`는 처음 금액 그대로 둡니다. 무엇을 얼마에 샀었는지 남기기 위해서입니다. 남은 금액은 `remainingPrice`로 따로 보여줍니다. 좌석에는 `cancelled`, 상품에는 `canceledQuantity`가 붙습니다.
- 예매는 부분 취소 뒤에도 `RESERVED`이고, 남은 좌석을 모두 취소하면 `CANCELLED`가 됩니다. 구매는 부분 환불 뒤에도 `PAID`이고, 남은 수량이 없으면 `CANCELLED`가 됩니다.

#### 예매와 매점의 취소 정책이 다른 이유

| | 예매 | 매점 |
|---|---|---|
| 누가 | 회원 | 매장(관리자). 회원 취소 API는 없습니다 |
| 단위 | 좌석. 좌석 하나는 한 번만 취소됩니다 | 상품 라인 × 수량. 한 라인을 여러 번 나눠 환불할 수 있습니다 |
| 기한 | 상영 20분 전. T1 시각 기준이라 토스를 기다리는 사이 기한이 지나도 다시 거절하지 않습니다 | 없음 (수령 개념이 없음) |
| 자원 | 환불이 확인되면 좌석을 다시 팔 수 있게 됩니다 | **재고를 되돌리지 않습니다** |
| 결제 전 | 선점(`PENDING`)은 토스 호출 없이 바로 취소. 부분 취소는 막습니다(결제 주문 금액이 이미 정해져 있음) | 결제 전 주문은 재고를 잡지 않아 취소할 것이 없습니다 |

실제 CGV 매점은 제조가 시작되면 취소가 되지 않습니다. 이 모델에는 제조·수령 상태가 없어서 "결제 = 제조 접수"로 봤습니다. 수량 단위 환불이 실제로 생기는 것은 품절·기계 고장처럼 매장 쪽 사정이라, 매장이 제공하지 못한 수량만큼 관리자가 환불하는 방식으로 했습니다. 환불 사유가 "만들 수 없음"이므로 재고를 되돌리면 다른 회원이 만들 수 없는 상품을 사게 됩니다.

#### 과제에서 고려하라고 한 상황

| 상황 | 동작 | 응답 | 테스트 |
|---|---|---|---|
| **이미 취소된 결제를 다시 취소** | 전체 취소된 예매는 예매 상태에서, 취소된 좌석은 좌석에서 걸려 토스를 부르지 않습니다. 결제가 이미 `CANCELED`이면 `verifyCancelable`이 막습니다 | 409 `ALREADY_CANCELLED` · `SEAT_ALREADY_CANCELLED` · `PAYMENT_ALREADY_CANCELED`, 매점 `PURCHASE_NOT_PAID` | `ReservationControllerTest.전체_취소된_예매를_다시_취소하면_409이고_토스를_부르지_않는다`, `…이미_취소된_좌석을_다시_취소하면_409이고_토스를_부르지_않는다`, `PurchaseAdminControllerTest.결제가_끝나지_않았거나_이미_모두_환불된_구매는_환불할_수_없다` |
| **결제 금액보다 많이 취소** | 금액을 받지 않으므로 구조상 생길 수 없습니다. 고를 수 있는 것은 남은 좌석·수량뿐입니다. 계산 쪽 버그를 막는 마지막 검사가 엔티티에 있습니다 | 409 `CANCEL_QUANTITY_EXCEEDED` / `CANCEL_AMOUNT_EXCEEDS_BALANCE` | `PurchaseAdminControllerTest.남은_수량보다_많이_환불하면_409이고_토스를_부르지_않는다`, `PaymentCancelTest.잔액보다_큰_취소는_요청을_만들_수_없다` |
| **취소 중 PG 호출 실패 — 거절** | 취소 `FAILED`. 좌석·수량·결제는 그대로이고 다시 요청할 수 있습니다 | 409 `PAYMENT_CANCEL_REJECTED` | `ReservationControllerTest.토스가_취소를_거절하면_좌석과_결제는_그대로이고_다시_취소할_수_있다`, `PurchaseAdminControllerTest.토스가_거절하면_수량과_결제는_그대로이고_다시_환불할_수_있다` |
| **취소 중 PG 호출 실패 — 결과 모름** (타임아웃, 5xx, 연결 실패, `ALREADY_CANCELED_PAYMENT` 등) | 취소를 `IN_PROGRESS`로 두고 좌석을 놓지 않습니다. 같은 결제의 새 취소는 막습니다. 회원(또는 2분 뒤 스케줄러)이 `…/cancellations/{cancelId}/retry`로 **같은 멱등키**를 다시 보내 결과를 맞춥니다. 토스 멱등키 유효기간(15일) 안쪽인 14일까지만 재시도합니다 | 504 `PAYMENT_CANCEL_RESULT_UNKNOWN`, 기한 뒤 409 `PAYMENT_CANCEL_RETRY_EXPIRED` | `ReservationControllerTest.취소_결과를_모르면_504이고_좌석을_놓지_않으며_같은_멱등키로_재시도하면_반영된다`, `…요청_후_14일이_지난_취소는_재시도하지_않는다` |

결과를 모르는 취소를 거절로 처리하지 않는 이유는 이렇습니다. 거절로 두면 회원이 다시 취소를 요청하고, 그 요청은 **새 멱등키**를 받습니다. 첫 취소가 실제로는 성공했다면 같은 금액이 한 번 더 환불됩니다. 부분 취소는 잔액이 남아 있어 토스도 이 요청을 막지 않습니다. 멱등키를 승인과 다르게 정한 이유는 「중복 결제와 멱등성」에 있습니다.

### 결제 API

| 구분 | 메서드 · 경로 | 하는 일 |
|---|---|---|
| 공개 | `GET /api/payments/widget-config` | 결제위젯 클라이언트 키 |
| 회원 · 예매 | `POST /api/reservations/{id}/payments` | 결제 주문 생성 |
| | `POST /api/reservations/{id}/payments/confirm` | 결제 승인 |
| | `GET /api/reservations/{id}/payments` | 결제 조회 (DB) |
| | `DELETE /api/reservations/{id}` | 결제 전이면 선점 취소, 결제됐으면 남은 좌석 전체 환불 |
| | `POST /api/reservations/{id}/cancellations` | 좌석 부분 취소 `{"seats":[{"rowNum":1,"colNum":2}]}` |
| | `POST /api/reservations/{id}/cancellations/{cancelId}/retry` | 결과 불명 취소 재시도 |
| 회원 · 매점 | `POST /api/purchases` | 구매 주문 + 결제 주문 생성 |
| | `POST /api/purchases/{id}/payments/confirm` | 결제 승인 |
| | `GET /api/purchases/{id}/payment` | 결제 조회 (DB) |
| 관리자 | `GET /api/admin/payments/{orderId}/toss` | 우리 DB와 토스 대조 |
| | `DELETE /api/admin/purchases/{id}` | 매점 전체 환불 |
| | `POST /api/admin/purchases/{id}/cancellations` | 매점 수량 부분 환불 `{"items":[{"productId":1,"quantity":2}]}` |
| | `POST /api/admin/purchases/{id}/cancellations/{cancelId}/retry` | 결과 불명 환불 재시도 |
| | `POST /api/admin/reservations/payments/{orderId}/recovery` | 결과 불명 예매 결제 정리 |
| | `POST /api/admin/purchases/payments/{orderId}/recovery` | 결과 불명 매점 결제 정리 |

## 동시성 문제

여러 요청이 동시에 들어와도 의도대로 데이터가 처리되는지 서비스 전체를 다시 살펴봤습니다. 앞부분은 동시성 문제와 해결 방법을 조사한 내용이고, 뒷부분은 이 서비스에서 문제가 생길 수 있는 지점마다 문제를 실제로 재현한 방법, 고려한 방법과 고른 방법, 적용 전후의 테스트 결과입니다.

### 동시성 문제란

둘 이상의 요청이 **같은 데이터**를 동시에 읽고 쓸 때, 실행 순서가 섞이면서 하나씩 실행했을 때와 다른 결과가 나오는 문제입니다(경쟁 상태, race condition).

### 왜 발생하나요

- **요청마다 스레드와 트랜잭션이 따로입니다.** Spring MVC(Tomcat)는 요청 하나를 스레드 하나가 처리하고, 각 스레드는 자기 DB 커넥션과 트랜잭션을 씁니다. 같은 좌석을 고른 두 요청은 서로를 모른 채 동시에 실행됩니다.
- **"읽고 → 판단하고 → 쓴다"는 쿼리 여러 개입니다.** 코드로는 한 동작처럼 보여도 SELECT와 UPDATE 사이에 틈이 있고, 그 틈에 다른 트랜잭션이 끼어들 수 있습니다.
- **일반 SELECT는 스냅샷을 읽습니다.** MySQL InnoDB의 기본 격리 수준(REPEATABLE READ)에서 잠금 없는 SELECT는 트랜잭션의 첫 읽기 시점에 만들어진 스냅샷을 봅니다. 두 요청이 모두 "아직 비어 있음"을 보고 둘 다 다음 단계로 넘어갑니다.

대표적인 모양은 다음과 같습니다.

| 유형 | 모양 | 예 |
|---|---|---|
| 갱신 손실 (Lost Update) | 둘이 같은 값을 읽고 각자 고쳐 쓰면 먼저 쓴 쪽이 사라집니다 | 재고 10을 둘이 읽고 각자 9로 씀 → 2개 팔렸는데 재고 9 |
| 확인 후 실행 (Check-then-Act) | 확인한 시점과 쓰는 시점 사이에 상태가 바뀝니다 | 빈 좌석인지 확인한 뒤 INSERT하기 전에 다른 사람이 먼저 INSERT |
| 중복 생성 | 둘 다 "없음"을 확인하고 둘 다 INSERT합니다 | 같은 아이디로 동시 회원가입 |
| 쓰기 왜곡 (Write Skew) | 서로 **다른 행**을 쓰지만 함께 지켜야 할 규칙이 깨집니다 | "1인당 8석" 규칙에서 4석 요청 두 건이 각자 기존 4석을 보고 통과 → 12석 |
| 교착 (Deadlock) | 둘이 서로가 쥔 락을 기다립니다 | A1→A2 순서와 A2→A1 순서로 잠그는 두 요청 |

Dirty Read, Non-Repeatable Read, Phantom Read 같은 격리 수준 이상 현상도 뿌리는 같습니다. 다만 InnoDB의 REPEATABLE READ는 Dirty Read와 Non-Repeatable Read는 막아도 **갱신 손실과 쓰기 왜곡은 막지 않습니다.** 격리 수준만으로 해결되지 않는 이유입니다.

### 해결 방법

#### 1. 애플리케이션 락 — `synchronized`, `ReentrantLock`

- **원리:** 한 JVM 안에서 임계 영역에 스레드를 하나씩만 들여보냅니다.
- **장점:** 가장 간단하고 추가 인프라가 필요 없습니다.
- **단점:**
  - **서버가 두 대 이상이면 소용이 없습니다.** JVM마다 따로 잠그기 때문입니다.
  - `@Transactional` 메서드에 붙이면 **락이 커밋보다 먼저 풀립니다.** 커밋은 프록시가 메서드 밖에서 하므로, 다음 스레드가 아직 커밋되지 않은 상태를 읽고 다시 경쟁합니다.
  - 좌석 하나를 지키려다 예매 전체를 한 줄로 세우는 식으로 락 단위가 거칠어지기 쉽습니다.
- **적합한 상황:** 서버가 한 대이고 DB 밖의 메모리 상태를 지킬 때. DB 데이터 보호에는 맞지 않습니다.

#### 2. 격리 수준 올리기 — `SERIALIZABLE`

- **원리:** 모든 읽기에 공유 락을 걸어, 트랜잭션들이 하나씩 순서대로 실행된 것과 같은 결과를 만듭니다.
- **장점:** 코드를 고치지 않고 이상 현상을 모두 막습니다.
- **단점:** 전역으로 느려집니다. 공유 락끼리 서로의 쓰기를 막아 교착이 나고, DB가 한쪽을 강제로 롤백하는 방식으로 정합성을 지킵니다.
- **적합한 상황:** 거의 없습니다. 특정 트랜잭션에만 국소적으로 쓰는 정도입니다.

#### 3. 비관적 락 — `SELECT ... FOR UPDATE`

- **원리:** "충돌이 날 것이다"라고 보고, 읽는 순간 행에 배타 락을 겁니다. 다른 트랜잭션은 커밋까지 기다리고, 기다린 뒤에는 스냅샷이 아니라 **최신 커밋본**을 읽습니다. JPA에서는 `@Lock(LockModeType.PESSIMISTIC_WRITE)`입니다.
- **장점:** 정합성이 확실합니다. 판정 규칙을 엔티티 메서드에 그대로 둘 수 있습니다. 충돌이 잦아도 재시도 없이 줄을 서서 처리됩니다.
- **단점:**
  - **잠글 행이 있어야 합니다.** 아직 없는 데이터를 만드는 경쟁에는 쓸 수 없습니다.
  - 기다리는 동안 DB 커넥션을 쥡니다. 락 대기 한도가 커넥션 대기 한도보다 길면 경합과 무관한 API가 먼저 죽습니다.
  - 여러 행을 잠그면 잠그는 순서에 따라 교착이 납니다. 획득 순서를 코드로 고정해야 합니다.
  - MySQL은 `FOR UPDATE` 쿼리에 조인한 행까지 잠급니다. 락 범위가 의도보다 넓어지기 쉽습니다.
  - 락을 쥔 채 외부 API(PG 결제 등)를 호출하면 그동안 다른 요청이 모두 기다립니다.
- **적합한 상황:** 충돌이 잦고, 경합 단위와 정확히 맞는 행이 있을 때(재고, 잔액).

#### 4. 낙관적 락 — `@Version`

- **원리:** 락 없이 읽고, 쓸 때 `UPDATE ... SET version = v + 1 WHERE id = ? AND version = v`로 그사이에 누가 바꿨는지 확인합니다. 0행이면 충돌로 보고 예외를 던집니다.
- **장점:** DB 락도 대기도 없습니다. 충돌이 드물면 처리량이 가장 좋습니다.
- **단점:**
  - 진 요청은 **커밋 시점에야** 실패합니다. 그때까지 한 일은 버려집니다.
  - 재시도 로직이 필요하고, 충돌이 잦은 곳에서는 재시도가 몰려 오히려 느려집니다.
  - 엔티티에 버전 컬럼을 더해야 합니다. JPQL 벌크 UPDATE는 버전을 자동으로 올리지 않아 빠뜨리기 쉽습니다.
- **적합한 상황:** 충돌이 드문 수정(회원 정보 수정, 게시글 편집).

#### 5. 조건부 UPDATE — Compare-And-Set

- **원리:** 확인과 쓰기를 SQL 한 문장으로 합칩니다. `UPDATE stock SET quantity = quantity - 2 WHERE id = ? AND quantity - 2 >= 1`처럼 조건을 WHERE에 넣고, 영향받은 행 수로 성공 여부를 판단합니다.
- **장점:** 가장 빠르고, 락을 쥐는 시간이 문장 하나뿐입니다. 버전 컬럼도 필요 없습니다.
- **단점:** 판정 규칙이 엔티티가 아니라 SQL에 들어가 **규칙이 두 곳에 생깁니다.** 0행일 때 원인(품절인지, 이미 상태가 바뀐 것인지)을 알려면 다시 읽어야 합니다. 영속성 컨텍스트를 거치지 않아 메모리의 엔티티와 어긋날 수 있습니다.
- **적합한 상황:** 단순 증감, 조건이 단순한 상태 전이 한 번.

#### 6. DB 유니크 제약

- **원리:** "이 조합은 하나만 존재한다"를 DB가 보장합니다. 동시에 INSERT해도 하나만 성공하고 나머지는 중복 키 오류가 납니다.
- **장점:** 서버가 몇 대든 확실합니다. 잠글 행이 없어도, 즉 **아직 없는 것을 만드는 경쟁**에도 쓸 수 있습니다.
- **단점:** "유일하다"만 표현할 수 있습니다. 수량이나 합계 규칙은 막지 못합니다. InnoDB는 중복 키 INSERT를 바로 거절하지 않고 앞선 트랜잭션이 끝날 때까지 커넥션을 쥔 채 기다립니다.
- **적합한 상황:** 중복 생성 방지(아이디, 찜, 좌석 점유).

#### 7. 분산 락 — Redis(Redisson), MySQL `GET_LOCK()`

- **원리:** 여러 서버가 함께 보는 외부 저장소에 락 키를 잡습니다.
- **장점:** 서버가 여러 대여도 동작합니다. DB 행으로 표현되지 않는 자원이나 여러 DB에 걸친 작업도 묶을 수 있습니다.
- **단점:** 인프라가 늘어납니다. 락 만료와 해제 실패를 직접 처리해야 하고, 트랜잭션 커밋과 락 해제의 순서도 맞춰야 합니다.
- **적합한 상황:** DB 하나로 해결할 수 없을 때(서비스가 여러 DB로 나뉜 경우, 외부 API 호출을 직렬화해야 하는 경우).

#### 8. 큐로 줄 세우기 — 메시지 큐, 대기열

- **원리:** 요청을 큐에 넣고 소비자가 하나씩 처리합니다.
- **장점:** 순간적으로 몰리는 트래픽을 흡수합니다(티켓 오픈, 선착순 이벤트).
- **단점:** 비동기라 즉시 결과를 응답하기 어렵고 인프라가 큽니다.
- **적합한 상황:** 요청량이 DB가 감당할 수 있는 한계를 넘을 때.

### 어떤 상황에 어떤 방법을

| 상황 | 방법 |
|---|---|
| 없던 것을 만드는데 하나만 있어야 함 | 유니크 제약 |
| 있는 행의 값을 여러 요청이 자주 고침 | 비관적 락 |
| 있는 행을 가끔 고치고 충돌이 드묾 | 낙관적 락 |
| 단순 증감, 조건이 단순한 상태 전이 | 조건부 UPDATE |
| 서버가 여러 대이고 DB 행으로 표현할 수 없는 자원 | 분산 락 |
| 순간 트래픽이 DB 한계를 넘음 | 큐 / 대기열 |

고를 때는 두 가지를 먼저 물었습니다. **잠글 행이 있는가?** 그리고 **충돌이 얼마나 자주 나는가?**

### CGV 서비스의 동시성 지점

각 지점에서 동시성 제어를 뺀 코드("적용 전")와 지금 코드("적용 후")로 **같은 테스트**를 5번씩 돌린 결과입니다. 재현 방법은 바로 아래 「재현 방법」에 있습니다.

| # | 지점 | 동시에 오면 생기는 일 | 유형 | 택한 방법 | 적용 전 (5회) | 적용 후 (5회) |
|---|---|---|---|---|---|---|
| 1 | 좌석 선점 | 같은 좌석이 두 사람에게 팔림 | 중복 생성 | 유니크 제약 | 같은 좌석 2건 성공, 5/5 | 1건 성공, 5/5 |
| 2 | 만료된 선점 회수 | 빈 좌석을 "이미 선택됨"으로 거절 | 상태 전이 경쟁 | 조건부 UPDATE | 간헐 실패 (고정 불가, 본문 참고) | — |
| 3 | 매점 재고 차감 | 재고보다 많이 팔림 | 갱신 손실 | 비관적 락 | 4개 팔고 재고는 2만 줄어듦, 5/5 | 판매 가능 수량만 팔림, 5/5 |
| 4 | 회원가입 · 찜 | 같은 아이디 두 계정, 같은 찜 여러 행 | 중복 생성 | 유니크 제약 | 계정 2개 / 찜 6행, 5/5 | 1개, 5/5 |
| 5 | 리프레시 토큰 재발급 | 토큰 하나로 새 토큰 여러 개 | 확인 후 실행 | 비관적 락 | 10건 모두 재발급, 5/5 | 1건만, 5/5 |
| 6 | 결제 승인 중 선점 만료 | 돈은 빠졌는데 좌석은 남의 것 | 확인 후 실행 | 선점 연장 | 다른 회원이 좌석을 가져감, 5/5 | 좌석 유지, 5/5 |
| 7 | 같은 예매의 두 결제 동시 승인 | 이중 결제 | 확인 후 실행 | 예매 행 잠금 + 승인 중 상태 | 둘 다 승인, 5/5 | 1건만, 5/5 |
| 8 | 외부 결제 호출 동안의 락 | 토스를 기다리는 동안 같은 예매의 요청이 전부 멈춤 | 락 보유 시간 | 트랜잭션 분리 (T1 → 토스 → T2) | 락 대기 초과 예외, 5/5 | 즉시 409, 5/5 |
| 9 | 결제된 예매 취소 | 같은 결제에 환불 두 건이 함께 나감 | 확인 후 실행 | 예매 행 잠금 + 진행 중 취소 1건 | 둘 다 환불, 5/5 | 1건만, 5/5 |
| 10 | 매점 환불 | 같은 구매에 환불 두 건이 함께 나감 | 확인 후 실행 | 구매 행 잠금 + 진행 중 취소 1건 | 둘 다 환불, 5/5 | 1건만, 5/5 |

좌석과 매점은 같은 서비스 안에서도 동시성의 모양이 다릅니다. 이 차이가 방법을 가른 출발점입니다.

| | 좌석 | 매점 |
|---|---|---|
| 점유 시점 | 좌석을 **선택하는 순간** (`PENDING`, 10분) | **결제 승인 직전** (승인 T1) |
| 다투는 대상 | 특정 좌석 하나. 다른 좌석으로 대신할 수 없습니다 | 수량. 누가 가져가든 개수만 맞으면 됩니다 |
| 문제 유형 | 같은 좌석을 두 번 INSERT (중복 생성) | 같은 재고 행을 둘이 차감 (갱신 손실) |
| 잠글 행 | **없습니다.** 좌석 테이블이 없고, 아직 없는 행을 만드는 경쟁입니다 | **있습니다.** `stock`(지점 × 상품) 한 행 |
| 충돌을 알게 되는 때 | 좌석 선택 시. 다른 좌석을 고르면 됩니다 | 결제 승인 시. 토스를 부르기 전에 품절을 안내받습니다 |

주문을 만드는 것만으로 재고를 잡지 않은 것도 같은 이유입니다. 상품은 대신할 수 있는 수량이라 미리 붙잡아 둘 "특정 대상"이 없습니다. 주문하는 순간 재고를 빼면 선점 만료와 복구라는 좌석의 숙제가 매점에 그대로 생기는데 얻는 것이 없습니다.

### 재현 방법

**"적용 전"은 변형 실험으로 만듭니다.** 제어 코드를 잠시 빼고 같은 테스트를 돌려 실패를 기록한 뒤 되돌렸습니다. 지점마다 뺀 코드를 diff로 적어 두었으니 같은 변경으로 누구든 다시 낼 수 있습니다.

- **git 이력으로는 만들 수 없습니다.** 재고 락과 좌석 유니크 제약은 기능의 첫 커밋부터 들어 있어 "제어 없는 커밋"이 없습니다. 예전 커밋은 mock 결제·H2 시절이라 지금 테스트가 돌지도 않습니다.
- **운영 코드에 "제어 끄기" 스위치를 두지 않았습니다.** 테스트를 위한 분기가 운영 코드에 남기 때문입니다.
- 유니크 제약은 엔티티를 고치지 않고 테스트 시작 시 `ALTER TABLE ... DROP INDEX`로 지웠습니다. 결과는 같습니다.

**순서는 게이트로 고정합니다.** 동시에 출발만 시키는 테스트는 한 요청이 쓰기까지 마친 뒤에 다른 요청이 읽으면 경합 없이 통과합니다. 그러면 "제어가 없어도 통과"가 우연히 나올 수 있어 재현으로 치지 않았습니다.

- `support/RaceGate`는 지정한 저장소 메서드가 반환된 직후, 즉 **"읽었고 아직 쓰지 않은" 순간**에 요청들을 서로 기다리게 합니다. 둘 다 같은 값을 본 뒤 쓰게 되므로, 제어가 없으면 **매번** 문제가 납니다.
- 운영 코드는 건드리지 않았습니다. `support/RaceGateConfig`가 테스트 컨텍스트의 저장소 프록시 맨 앞에 인터셉터를 붙이고, 게이트를 걸지 않은 메서드는 그대로 지나갑니다. `MySqlContainerConfig`가 이 설정을 함께 가져와 컨텍스트(컨테이너)가 늘지 않습니다.
- 제어가 있으면 늦은 요청은 DB 락에 막혀 게이트에 오지 못합니다. 앞선 요청이 그 요청을 끝없이 기다리면 JVM의 대기와 DB의 대기가 서로를 무는 교착이 되므로, **1초가 지나면 혼자 지나갑니다.** 1초는 락 대기 한도(3초)보다 짧아 늦은 요청이 락 예외 없이 앞선 커밋을 기다릴 수 있습니다. 이 시간은 테스트 시간에만 영향을 주고 결과는 바꾸지 않습니다.
- `gate.aloneCount()`로 경합이 실제로 겹쳤는지 단언합니다. 유니크 제약 지점은 0(둘 다 확인을 통과했다 = 경합이 났다), 락 지점은 1(늦은 요청이 락에 막혀 게이트에 오지 못했다 = 락이 줄을 세웠다)입니다.
- 결제·환불처럼 외부 호출을 낀 지점은 원래 테스트가 토스 호출을 래치로 붙잡아 두고 그 순간에 다른 요청을 보냅니다. 이 방식도 순서가 고정됩니다.

![게이트로 경합 순서를 고정한다: 잠금이 없으면 두 승인이 같은 재고 3을 읽고 함께 게이트를 지나 둘 다 1을 저장한다. FOR UPDATE가 있으면 늦은 승인이 앞선 커밋까지 막혀 재고 1을 읽고 품절로 끝난다](docs/images/concurrency/concurrency-gate.svg)

재고 3(판매 가능 2)에 2개씩 승인 두 건을 보낸 3번 지점의 예입니다. 잠금이 없으면 두 승인이 같은 3을 읽고 게이트를 함께 지나 둘 다 1을 저장합니다. 잠금이 있으면 B는 게이트에 오기도 전에 막히고, A가 1초 뒤 혼자 지나갑니다. 이때 `aloneCount`가 1이 됩니다.

**반복과 실행.** 반복은 실험할 때만 대상 테스트의 `@Test`를 `@RepeatedTest(5)`로 바꿔 돌렸습니다. 실행 명령은 `./gradlew test --tests "*PurchaseConcurrencyTest.두_승인이*"`와 같고 Docker가 켜져 있어야 합니다. 실험이 끝날 때마다 원본으로 되돌리고 SHA-256이 실험 전과 같은지 확인했습니다.

**기존 다중 스레드 테스트(20스레드 등)는 그대로 둡니다.** 부하가 몰릴 때의 회귀 테스트로는 쓸모가 있지만, 겹침이 타이밍에 달려 있어 재현 근거로는 쓰지 않았습니다.

#### 1. 좌석 선점 — 유니크 제약

**문제.** 예매는 "이미 선택된 좌석인지 확인 → 좌석 행 INSERT" 순서입니다. 민수와 지연이 같은 B3를 동시에 고르면 둘 다 확인 단계에서 빈 좌석을 보고 둘 다 INSERT합니다. 확인과 INSERT 사이의 틈은 애플리케이션 코드로는 원리적으로 닫을 수 없습니다.

**재현.** `ReservationConcurrencyTest.두_요청이_모두_빈_좌석을_확인한_뒤_INSERT해도_한_건만_성공한다`. 두 요청을 사전 점유 확인(`findOccupiedPositionsByScreeningId`) 직후에 세워, 둘 다 "빈 좌석"을 본 뒤 INSERT하게 합니다.

```java
// 적용 전: 테스트 시작 시 유니크 인덱스를 지운다. 외래 키가 이 인덱스를 쓰므로 screening_id 인덱스를 먼저 만든다
em.createNativeQuery("alter table reservation_seat add index ix_tmp (screening_id), "
        + "drop index uk_seat_screening_row_col_release").executeUpdate();
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **유니크 제약** | **택합니다.** "같은 좌석은 하나"는 유일성 규칙 그 자체이고, 서버가 몇 대든 DB가 보장합니다 |
| 비관적 락 | 좌석 마스터 테이블이 없어 예매 전의 좌석에는 잠글 행이 없습니다. 잠글 수 있는 것은 `screening` 행뿐인데, 그러면 B3를 고른 사람과 J12를 고른 사람까지 회차 전체가 한 줄로 섭니다 |
| 낙관적 락 | 버전을 비교할 기존 행이 없습니다 |
| `synchronized` | 서버가 두 대면 소용없고, `@Transactional` 안에서는 커밋 전에 풀려 다음 요청이 아직 커밋되지 않은 상태를 읽습니다 |
| `SERIALIZABLE` | 사전 확인 SELECT가 공유·간격 락을 걸어 두 INSERT가 서로의 락을 기다리다 교착이 나고, DB가 한쪽을 롤백해서 막습니다. 교착에 기대는 방식이고 회차 조회 전체가 느려집니다 |

**선택과 이유.** `reservation_seat`에 `UNIQUE (screening_id, row_num, col_num, release_key)`를 걸고, `saveAndFlush()`의 중복 키 오류를 409 `SEAT_ALREADY_RESERVED`로 바꿉니다. 사전 확인은 경쟁이 없을 때 친절한 응답을 주기 위한 것이고, 최종 방어선은 제약입니다. 결제 전 선점(`PENDING`)도 좌석 행을 실제로 만들어서 같은 제약이 선점 잠금 역할까지 합니다. 잠글 행이 없는 "아직 없는 것을 만드는 경쟁"에서 쓸 수 있는 방법이 유니크 제약뿐이라는 점이 결정적이었습니다. 취소한 좌석을 다시 팔 수 있게 하는 `release_key`는 「중복 예매 방지」에, 락 범위·커넥션 보호는 「좌석 경합 — 락의 범위와 순서」에 있습니다.

**결과.**

| | 적용 전 (제약 없음) | 적용 후 |
|---|---|---|
| 같은 좌석 요청 2건 | **2건 성공**, 점유 좌석 2행 (같은 좌석) | 1건 성공 + 1건 `SEAT_ALREADY_RESERVED` |
| 5회 반복 | 5/5 실패 | 5/5 통과 |

```
적용 전  org.opentest4j.AssertionFailedError: expected: 1L but was: 2L    ← 성공 건수
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 제약 없이 같은 좌석 2건 성공

![적용 전 — 제약 없이 같은 좌석 2건 성공](docs/images/concurrency/02-before-seat.png)

적용 후 — 5회 모두 통과

![적용 후 — 5회 모두 통과](docs/images/concurrency/18-after-seat.png)

</details>

**같은 회차의 다른 좌석은 동시성 문제가 아닙니다.** B3와 J12는 유니크 키가 다른 행을 INSERT할 뿐이라 서로를 막지 않습니다. 잔여석을 컬럼에 두지 않고 조회할 때 세는 것, 회차 행을 잠그지 않는 것, 만료 선점 정리를 요청한 좌석으로 좁힌 것이 이를 지킵니다. 다만 "한 회원이 한 회차에 최대 8석"처럼 요청 여러 건에 걸친 규칙이 생기면 서로 다른 행을 쓰는 요청끼리 규칙을 공유해 쓰기 왜곡이 생깁니다. 지금은 요청 하나에 8석까지만 제한하므로 해당하지 않습니다.

**좌석 순서 교착 — 정렬.** `[A1, A2]`와 `[A2, A1]`을 동시에 요청하면 서로가 INSERT한 행을 기다리는 교착이 납니다. 좌석을 (행, 열) 순으로 정렬해 INSERT 순서(= 락 획득 순서)를 고정했습니다. 정렬을 빼고(`orderedSeats(req.seats())` → `req.seats()`) `ReservationConcurrencyTest.좌석_순서가_엇갈린_요청이_겹쳐도_매핑되지_않은_예외가_나오지_않는다`를 5번 돌렸습니다.

| | 정렬 없음 | 정렬 있음 |
|---|---|---|
| 교착(1213)으로 롤백된 요청 (5회 × 6요청, 두 번 실행) | **30건 중 21건, 7건** | 0건, 0건 |
| 테스트 결과 | 5/5 통과 | 5/5 통과 |

**이 테스트는 정렬이 빠진 것을 잡지 못합니다.** InnoDB가 교착을 즉시 감지해 한쪽만 롤백하고, 그 요청이 409 `SEAT_RESERVATION_CONFLICT`로 매핑되어 "1건 성공 + 매핑된 실패"라는 단언이 그대로 맞기 때문입니다. 정렬의 효과는 결과가 아니라 교착 롤백 횟수에서 드러나고, 그 횟수도 실행마다 다릅니다. 좌석 경합 세션에서 정렬을 뺐을 때 "성공 0건"이었던 것은 그때 테스트 DB였던 H2의 값입니다. MySQL에서는 결과가 달랐고, 테스트가 무엇을 지키는지도 그만큼 약해졌습니다.

<details>
<summary>테스트 리포트 캡처</summary>

정렬을 빼도 5회 모두 통과한다

![정렬을 빼도 5회 모두 통과한다](docs/images/concurrency/07-seat-order-without-sort.png)

</details>

#### 2. 만료된 선점 회수 — 조건부 UPDATE

**문제.** 만료된 선점 X가 (2,1)·(2,2)를 잡고 있을 때, 민수가 (2,1)을, 지연이 (2,2)를 동시에 고르면 두 요청이 같은 선점 X를 동시에 해제합니다. 처음에는 엔티티를 고쳐 더티 체킹으로 UPDATE했는데, 늦은 쪽은 앞선 쪽이 이미 바꾼 값을 같은 값으로 다시 덮어썼습니다. InnoDB는 값이 바뀌지 않는 UPDATE에는 새 행 버전을 만들지 않아서, 늦은 쪽의 스냅샷에는 예매는 `EXPIRED`, 좌석은 아직 점유 중인 엇갈린 상태가 보였습니다. 그 결과 비어 있는 좌석을 "이미 선택된 좌석"으로 거절했습니다.

**재현 — 앱 수준에서는 순서를 고정할 수 없습니다.** 엇갈린 상태는 두 요청의 `updated_at`이 같은 시각이 되어야 생깁니다. 이 JVM(Windows)의 `LocalDateTime.now()`는 0.34~0.5ms 단위로만 바뀌어서 "같은 시각"은 우연히만 일어납니다. `updated_at`은 JPA Auditing이 시스템 시계로 채우므로 운영 코드를 바꾸지 않고는 고정할 수 없습니다. 그래서 이 지점은 이전 구현이 남긴 기록으로 대신합니다.

- 이전 구현에서 `ReservationConcurrencyTest.만료된_선점이_잡고_있던_좌석을_동시_요청이_다시_가져간다`가 **전체 실행 4회 중 2회** 실패했습니다(단독 실행 8회는 0회).
- 원인은 JDBC 커넥션 두 개로 순서를 고정한 실험으로 확정했습니다. B가 스냅샷 생성 → A가 만료 + 커밋 → B가 같은 행을 UPDATE → B가 사전 검사하는 순서입니다.

| 경우 | B의 UPDATE가 맞은 행 수 | B가 본 점유 좌석 |
|---|---|---|
| 예매 실제 변경, 좌석 값 같음 (이전 구현에서 시각이 겹친 경우) | 1 / 2 | **(2,1), (2,2)** |
| 둘 다 실제 변경 | 1 / 2 | 없음 |
| 조건부 UPDATE (지금) | 0 / 0 | 없음 |

`ReservationConcurrencyTest.만료_선점을_다른_요청이_먼저_풀어도_스냅샷이_앞선_요청은_빈_좌석을_잡는다`는 "B의 스냅샷이 A의 커밋보다 앞선다"까지만 고정합니다. 이 테스트는 지금 구현이 0행 경로에서도 빈 좌석을 잡는지를 지키고, 이전 구현을 늘 잡아내지는 못합니다.

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **조건부 UPDATE** | **택합니다.** `PENDING → EXPIRED` 한 번의 단순한 상태 전이입니다. 늦은 쪽은 0행이 되어 아무것도 건드리지 않습니다 |
| 비관적 락 | 늦은 쪽이 기다리게 됩니다. 이미 다른 요청이 풀어 줬으니 기다릴 이유가 없습니다 |
| 낙관적 락 | 늦은 쪽이 예외로 실패합니다. 실패할 이유가 없는 요청입니다 |

**선택과 이유.** `WHERE status = PENDING AND expires_at <= :now`를 건 조건부 UPDATE로 예매를 만료시키고, 1행이 바뀐 경우에만 `WHERE release_key = 0` 조건으로 좌석을 풉니다. 값이 실제로 바뀔 때만 행에 맞으므로 "UPDATE했는데 아무것도 안 바뀐" 상태가 생기지 않습니다. 늦은 쪽은 실패할 이유도 기다릴 이유도 없다는 점이 이 방법을 고른 이유입니다. 규칙이 엔티티와 쿼리 두 곳에 생기는 단점이 있어서, 만료 규칙은 쿼리 쪽에만 두고 `Reservation.expire()`를 지웠습니다.

#### 3. 매점 재고 차감 — 비관적 락

**문제.** 승인 T1은 "재고 읽기 → 남은 수량 확인 → 차감해서 저장"입니다. 팝콘 재고가 3(판매 가능 2)일 때 두 사람이 2개씩 동시에 사면, 둘 다 3을 읽고 둘 다 1로 저장합니다. 4개가 팔렸는데 재고는 2개만 줄었습니다.

**재현.** `PurchaseConcurrencyTest.두_승인이_같은_재고를_읽은_뒤_차감해도_판매_가능_수량을_넘지_않는다`. 주문 두 건(각 2개)을 미리 만들고, 두 승인을 재고 조회(`findByBranchIdAndProductIdForUpdate`) 직후에 세웁니다.

```diff
 // StockRepository
-    @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT s FROM Stock s WHERE s.branch.id = :branchId AND s.product.id = :productId")
     Optional<Stock> findByBranchIdAndProductIdForUpdate(...);
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **비관적 락** | **택합니다.** 경합 단위(지점 × 상품)와 정확히 맞는 행이 있습니다. 늦은 요청은 최신 커밋본을 읽어 `Stock.decrease()`가 그대로 품절을 판정합니다 |
| 낙관적 락 | 인기 상품은 충돌이 잦아 진 요청들이 커밋 시점에 실패하고 재시도가 몰립니다. 엔티티에 버전 컬럼도 필요합니다 |
| 조건부 UPDATE (`quantity - :n >= 1`) | 가장 빠르지만 "판매 가능 수량은 재고 − 1" 규칙이 엔티티와 쿼리 두 곳에 생깁니다. 0행일 때 품절인지 다른 이유인지 알려면 다시 읽어야 합니다 |
| DB `CHECK (quantity >= 1)` | 최종 방어선으로 함께 둡니다. **갱신 손실은 막지 못합니다.** 적용 전 실험에서 두 요청 모두 1을 써서 제약 위반이 아니었습니다 |
| `synchronized` | 서버가 여러 대면 소용없고 커밋 전에 풀립니다 |

**선택과 이유.** 재고 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 `Stock.decrease()`로 차감합니다. 좌석에서 비관적 락을 쓰지 않은 이유(잠글 행이 없음)가 여기에는 해당하지 않고, 충돌이 잦은 곳이라 재시도 없이 줄을 세우는 쪽이 맞습니다. 판정 규칙이 엔티티에 남는다는 점도 컸습니다.

- **락 범위:** 잠금 쿼리에 `product`를 조인하지 않습니다. MySQL의 `FOR UPDATE`는 조인한 행까지 잠가, 지점이 달라도 같은 상품 구매가 한 줄로 섭니다.
- **락 순서:** 상품 id 오름차순으로 한 건씩 잠가 [팝콘, 콜라]와 [콜라, 팝콘] 주문의 교착을 막습니다(`상품_순서가_엇갈린_주문이_겹쳐도_데드락_없이_전부_팔린다`).
- **락 보유 시간:** 재고는 승인 직전(T1)에 차감하고 T1 커밋과 함께 락을 놓습니다. 토스를 기다리는 동안에는 쥐지 않습니다(`토스_응답을_기다리는_동안에도_같은_상품의_다른_주문은_막히지_않는다`, 8번과 같은 구조).
- **락 대기 한도:** 3초를 넘으면 409 `STOCK_LOCK_CONFLICT`. "품절"이 아니라 "판정하지 못했으니 다시 시도"라 `OUT_OF_STOCK`과 구분했습니다.

**결과.**

| | 적용 전 (락 없음) | 적용 후 |
|---|---|---|
| 재고 3(판매 가능 2)에 2개씩 승인 2건 | **둘 다 성공, 4개 판매, 재고 3 → 1** (2만 줄어듦) | 1건 성공 + 1건 `OUT_OF_STOCK`, 재고 3 → 1 |
| 게이트 | 둘 다 같은 재고를 읽고 함께 통과 | 늦은 승인이 재고 행 락에 막혀 게이트에 못 옴 (`aloneCount = 1`) |
| 5회 반복 | 5/5 실패 | 5/5 통과 |

```
적용 전  org.opentest4j.AssertionFailedError: [줄어든 재고 = 판매 수량] expected: 4 but was: 2
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 줄어든 재고 2, 판매 수량 4

![적용 전 — 줄어든 재고 2, 판매 수량 4](docs/images/concurrency/03-before-stock.png)

적용 후 — 5회 모두 통과

![적용 후 — 5회 모두 통과](docs/images/concurrency/19-after-stock.png)

</details>

#### 4. 회원가입 · 찜 — 유니크 제약

**문제.** 둘 다 "이미 있는지 확인 → 없으면 INSERT"입니다. 같은 아이디로 가입 요청이 동시에 오거나 찜 버튼을 빠르게 두 번 누르면, 둘 다 "없음"을 보고 둘 다 INSERT합니다.

**재현.**
- 회원가입: `SignupConcurrencyTest.두_요청이_모두_없는_아이디를_확인한_뒤_가입해도_계정은_하나만_생긴다`. 두 요청을 중복 확인(`existsByLoginId`) 직후에 세웁니다.
- 찜: `BranchLikeConcurrencyTest.같은_찜이_동시에_들어와도_행은_하나만_남는다`(6스레드 동시 출발). 영화 찜은 코드 모양이 같아 따로 재현하지 않았습니다.

```java
// 적용 전 (회원가입): login_id 유니크 인덱스를 지운다. 이름은 Hibernate가 만들므로 조회해서 지운다
// 적용 전 (찜):
em.createNativeQuery("alter table branch_like add index ix_tmp (user_id), "
        + "drop index uk_branch_like_user_branch").executeUpdate();
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **유니크 제약** | **택합니다.** "아이디 하나에 계정 하나", "회원·극장 쌍에 찜 하나"는 유일성 규칙 그 자체입니다 |
| 비관적 락 | 회원가입은 아직 없는 행을 만드는 경쟁이라 잠글 행이 없습니다. 찜은 `users` 행을 잠글 수 있지만, 그러면 같은 회원의 모든 찜·해제가 한 줄로 섭니다 |
| 낙관적 락 | 버전을 비교할 기존 행이 없습니다 |
| 사전 확인만 | 확인과 INSERT 사이의 틈을 닫지 못합니다. 적용 전 실험이 바로 이 상태입니다 |

**선택과 이유.** 좌석과 같은 중복 생성 문제라 판단도 1번과 같습니다. `users.login_id`, `(user_id, branch_id)`, `(user_id, movie_id)`에 유니크 제약을 걸었습니다. 사전 확인은 순차적인 중복(재시도, 느린 더블클릭)을 친절하게 처리하고, 진짜 동시 요청은 제약이 막습니다. 회원가입은 409 `DUPLICATE_LOGIN_ID`, 찜은 409 `LIKE_REQUEST_CONFLICT`입니다.

찜에서 진 요청에 200을 주지 않는 이유가 있습니다. 결과 상태는 "찜함"이 맞지만, flush 중에 제약 위반이 나면 트랜잭션이 이미 롤백 전용이 되어 성공으로 돌려주면 커밋 시점에 500이 납니다. 다시 요청하면 사전 확인에 걸려 200이 됩니다.

**결과.**

| | 적용 전 (제약 없음) | 적용 후 |
|---|---|---|
| 같은 아이디 가입 2건 (게이트) | **계정 2개**, 5/5 실패 | 1개 + `DUPLICATE_LOGIN_ID`, 5/5 통과 |
| 같은 찜 6건 | **6행**, 5/5 실패 | 1행 + `LIKE_REQUEST_CONFLICT`, 5/5 통과 |

```
적용 전 (회원가입)  org.opentest4j.AssertionFailedError: expected: 1L but was: 2L    ← 계정 수
적용 전 (찜)        org.opentest4j.AssertionFailedError: expected: 1L but was: 6L    ← 찜 행 수
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 (회원가입) — 계정 2개

![적용 전 (회원가입) — 계정 2개](docs/images/concurrency/04-before-signup.png)

적용 후 (회원가입) — 5회 모두 통과

![적용 후 (회원가입) — 5회 모두 통과](docs/images/concurrency/20-after-signup.png)

적용 전 (찜) — 6행

![적용 전 (찜) — 6행](docs/images/concurrency/08-before-like.png)

</details>

찜 테스트는 게이트 없이 동시에 출발만 시키는 테스트라 원리상 겹침이 보장되지는 않습니다. 이번 5회는 모두 겹쳤습니다.

#### 5. 리프레시 토큰 재발급 — 비관적 락

**문제.** 재발급은 "토큰이 아직 쓰이지 않았는지 확인 → 사용 완료로 표시하고 새 토큰 발급"입니다. 같은 토큰으로 두 요청이 동시에 오면 둘 다 "미사용"을 보고 새 토큰을 하나씩 받습니다. 토큰 묶음이 여러 갈래로 갈라져 재사용 탐지가 무력해집니다.

**재현.** `RefreshTokenConcurrencyTest.같은_리프레시_토큰으로_동시_재발급하면_1건만_성공한다`(10건 동시, 실제 필터 체인).

```diff
 // RefreshTokenRepository
-    @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :tokenHash")
     Optional<RefreshToken> findByTokenHashForUpdate(...);
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **비관적 락** | **택합니다.** 경합 단위(토큰 하나)와 맞는 행이 있고, 늦은 요청이 최신 커밋본을 읽어 "사용 완료"를 봐야 재사용으로 탐지할 수 있습니다 |
| 조건부 UPDATE (`WHERE used_at IS NULL`) | 판정 규칙이 쿼리에 한 벌 더 생기고, 0행이면 "재사용"인지 "폐기"인지 알려고 다시 읽어야 합니다 |
| 낙관적 락 | 진 요청이 새 토큰을 이미 만든 뒤 커밋 때야 실패합니다 |

**선택과 이유.** 늦은 요청을 실패시키는 것이 아니라 **재사용으로 탐지**해야 하는 지점입니다. 그러려면 늦은 요청이 앞선 요청의 결과(사용 완료)를 읽어야 하고, 기다린 뒤 최신 값을 읽는 비관적 락이 그 요구에 맞습니다. 잠금 쿼리에 `user`를 조인하지 않는 것, 잠금 대기 초과를 401로 바꾸는 것은 「리프레시 토큰 — 동시 요청 처리 방식과 한계」에 있습니다.

**결과.**

| | 적용 전 (락 없음) | 적용 후 |
|---|---|---|
| 같은 토큰으로 10건 | **10건 모두 재발급**, 5/5 실패 | 1건 성공 + 9건 재사용 탐지, 5/5 통과 |

```
적용 전  java.lang.AssertionError: Expected size: 1 but was: 10    ← 200 응답 수
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 10건 모두 재발급

![적용 전 — 10건 모두 재발급](docs/images/concurrency/09-before-refresh.png)

</details>

3주차 세션 6에서 같은 변형을 했을 때는 10건 중 7건 성공이었습니다. MySQL로 옮긴 지금은 매번 10건 모두 성공합니다.

#### 6. 결제 승인 중 선점 만료 — 선점 연장

**문제.** 토스 승인 응답은 최대 60초가 걸립니다. 선점 9분 59초째에 승인을 시작하면, 토스를 기다리는 사이 선점이 만료됩니다. 그 순간 다른 회원이 같은 좌석을 고르면 만료 선점을 풀고 좌석을 가져갑니다. 토스는 승인(DONE)을 돌려주는데, 확정할 좌석이 없습니다.

```
민수  9분 59초  승인 T1 → 토스 호출 (응답 대기)
지연  10분 0초  같은 좌석 선택 → 민수의 선점을 EXPIRED로, 좌석 해제 → 지연의 선점 INSERT
토스            민수 결제 승인(DONE)
결과: 민수의 돈은 빠졌는데 좌석은 지연의 것
```

이 README의 첫 판에서 6번으로 적었던 "더티 체킹이 `EXPIRED`를 `RESERVED`로 덮어쓴다"는 문제는 결제를 토스로 바꾸며 이 모양이 되었습니다. 예매를 바꾸는 다른 경로(같은 예매의 결제·취소)는 7·9번이 막습니다.

**재현.** `ReservationPaymentConcurrencyTest.토스_호출_중에_원래_선점_시각이_지나도_다른_회원이_좌석을_가져가지_못한다`. 선점이 3초 남은 예매로 승인을 시작하고, 토스 호출을 래치로 붙잡은 채 3.5초 뒤 다른 회원이 같은 좌석을 고릅니다.

```diff
 // ReservationService.startPayment
         LocalDateTime expiresAtBeforePayment = reservation.getExpiresAt();
-        reservation.holdForPayment(LocalDateTime.now(clock));
         payment.startApproval();
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **승인 시작 시 선점 연장** | **택합니다.** 만료 시각을 `max(현재 만료, 지금 + 3분)`으로 늘립니다. 점유 판정 쿼리(`expires_at > now`)를 그대로 써서 결제 도메인을 조인하지 않아도 승인 중인 선점이 풀리지 않습니다 |
| 토스 호출 동안 예매 행 락 유지 | 만료 처리도 같은 행을 건드리므로 막히긴 합니다. 하지만 최대 60초 동안 락과 커넥션을 쥡니다(8번) |
| 점유 판정에 "승인 중 결제" 조건 추가 | 좌석 점유 쿼리마다 결제 테이블을 조인해야 하고, 예매 도메인이 결제 상태를 알아야 합니다 |
| 사후 환불 | 좌석을 잃은 뒤 돈을 돌려주는 보상입니다. 막을 수 있는 일을 사고로 만든 뒤 수습하는 셈입니다 |

**선택과 이유.** 3분은 토스 연결(5초) + 읽기(60초) 한도보다 넉넉한 값입니다. 승인이 거절되면 결제 전 만료 시각으로 되돌립니다. 남겨 두면 거절을 반복해 좌석을 계속 붙잡을 수 있기 때문입니다. 락을 쥐지 않고 "시간"으로 보호하므로 외부 호출 동안 아무것도 기다리지 않습니다. 결과 불명 결제가 3분을 넘기면 좌석이 풀릴 수 있습니다. 이때는 토스 조회로 승인을 확인한 뒤 좌석이 남아 있으면 확정하고, 잃었으면 환불합니다(「외부 API 통신과 예외 처리」).

**결과.**

| | 적용 전 (연장 없음) | 적용 후 |
|---|---|---|
| 토스 대기 중 원래 만료 시각이 지난 뒤 다른 회원의 선택 | **다른 회원의 선점 성공** (좌석을 가져감) | `SEAT_ALREADY_RESERVED`, 승인 후 `RESERVED` |
| 5회 반복 | 5/5 실패 | 5/5 통과 |

```
적용 전  java.lang.AssertionError: Expecting code to raise a throwable.    ← 다른 회원의 선택이 거절되지 않음
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 토스 대기 중 다른 회원의 선택이 성공

![적용 전 — 토스 대기 중 다른 회원의 선택이 성공](docs/images/concurrency/10-before-hold.png)

</details>

#### 7. 같은 예매의 두 결제 동시 승인 — 예매 행 잠금 + 승인 중 상태

**문제.** 두 탭에서 각각 결제위젯 인증을 마치면 주문번호가 둘이라, 같은 예매에 승인 요청이 두 건 옵니다. 승인 T1은 "같은 예매에 승인 중인 결제가 있는지 확인 → 없으면 `IN_PROGRESS`로 바꾸고 토스 호출"입니다. 둘이 동시에 확인하면 둘 다 "없음"을 보고 토스를 두 번 부릅니다. 이중 결제입니다.

**재현.** `ReservationPaymentConcurrencyTest.같은_예매의_두_주문을_동시에_승인해도_토스는_한_번만_불린다`. 두 승인을 예매 조회(`findOwnedForUpdate`) 직후에 세웁니다. 제어가 둘이라 변형도 두 가지로 했습니다.

```diff
 // 변형 A — ReservationRepository: 잠금 제거
-    @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("SELECT r FROM Reservation r WHERE r.id = :id AND r.user.id = :userId")
     Optional<Reservation> findOwnedForUpdate(...);

 // 변형 B — ReservationService.startPayment: 잠금은 두고 승인 중 확인만 제거
         payment.verifyAmount(req.amount());
-        ensureNoPaymentInProgress(id);
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **예매 행 비관적 락 + 승인 중(`IN_PROGRESS`) 상태** | **택합니다.** 아래 이유 |
| 토스 멱등키 | 멱등키가 `orderId`인데 두 탭의 주문번호가 달라 걸러지지 않습니다 |
| 조건부 UPDATE (`READY → IN_PROGRESS`) | 같은 주문의 중복 승인만 막습니다. 다른 주문 두 건은 각자 자기 행을 바꾸므로 둘 다 통과합니다 |
| 낙관적 락 (`Reservation.@Version`) | T1이 예매를 바꿔야 버전이 오르는데, 선점이 3분 넘게 남았으면 `holdForPayment`가 값을 바꾸지 않아 충돌이 나지 않습니다. 엔티티 변경도 필요합니다 |
| "예매당 승인 중 결제 1건" 유니크 제약 | MySQL은 조건부(부분) 유니크 인덱스가 없어 생성 컬럼으로 우회해야 하고, 엔티티 변경이 필요합니다 |

**선택과 이유. 두 제어는 맡은 일이 다르고, 하나만으로는 막지 못합니다.**

- **락은 T1끼리 줄을 세웁니다.** 결제 주문 생성, 승인 시작, 취소가 모두 같은 예매 행 잠금으로 시작합니다. 늦은 T1은 앞선 T1의 커밋을 기다린 뒤 최신 상태를 읽습니다. 락이 없으면(변형 A) 두 T1이 모두 스냅샷에서 "승인 중 없음"을 봅니다.
- **`IN_PROGRESS` 상태는 토스 호출 구간을 덮습니다.** 락은 T1 커밋과 함께 풀리고 토스 호출은 그 뒤에 일어납니다(8번). 토스를 기다리는 동안 들어온 요청을 막는 것은 커밋해 둔 "승인 중" 상태뿐입니다. 확인이 없으면(변형 B) 늦은 T1이 줄을 서서 기다렸다가도 그대로 토스를 부릅니다.
- **잠금 조회가 트랜잭션의 첫 쿼리여야 합니다.** InnoDB REPEATABLE READ는 첫 일반 SELECT 시점의 스냅샷을 씁니다. 잠금보다 먼저 일반 SELECT를 하면 앞 트랜잭션이 커밋한 `IN_PROGRESS`를 잠금을 얻은 뒤에도 보지 못합니다.
- 잠금 쿼리에 회차·영화를 조인하지 않습니다. 같은 영화의 결제가 전부 줄을 서게 되기 때문입니다.

![락과 승인 중 상태가 덮는 구간: 지금은 락이 T1만 덮고 IN_PROGRESS가 토스 호출 구간을 덮어, 토스 대기 중 도착한 B가 즉시 409로 끝난다. 확인을 빼면 B가 토스를 한 번 더 부르고, 한 트랜잭션으로 묶으면 B가 3초 뒤 락 대기 예외로 끝난다](docs/images/concurrency/concurrency-lock-vs-in-progress.svg)

세로 점선은 A가 토스 응답을 기다리는 동안 같은 예매로 들어온 결제 B이고, 점은 B가 무엇에 부딪히는지를 가리킵니다. 같은 그림이 9·10번(같은 결제의 두 환불)과 8번(한 트랜잭션으로 묶은 경우)을 함께 설명합니다. 락을 빼는 변형 A는 「재현 방법」의 그림과 같은 모양입니다.

**결과.**

| | 변형 A (락 없음) | 변형 B (확인 없음) | 적용 후 |
|---|---|---|---|
| 두 주문 동시 승인 | **둘 다 `RESERVED`** (토스 승인 2번) | **둘 다 `RESERVED`** | 1건 + `PAYMENT_IN_PROGRESS`/`RESERVATION_NOT_PENDING`, 토스 1번 |
| 게이트 | 둘이 함께 통과 | 늦은 쪽이 락에 막힘 | 늦은 쪽이 락에 막힘 (`aloneCount = 1`) |
| 5회 반복 | 5/5 실패 | 5/5 실패 | 5/5 통과 |

```
적용 전  java.lang.AssertionError: Expected size: 1 but was: 2 in:
         [ReservationResponse[..., status=RESERVED, ...], ReservationResponse[..., status=RESERVED, ...]]
```

<details>
<summary>테스트 리포트 캡처</summary>

변형 A (락 없음) — 두 승인 모두 성공

![변형 A (락 없음) — 두 승인 모두 성공](docs/images/concurrency/05-before-payment.png)

변형 B (승인 중 확인 없음) — 두 승인 모두 성공

![변형 B (승인 중 확인 없음) — 두 승인 모두 성공](docs/images/concurrency/11-before-payment-check.png)

적용 후 — 5회 모두 통과

![적용 후 — 5회 모두 통과](docs/images/concurrency/21-after-payment.png)

</details>

#### 8. 외부 결제 호출 동안의 락 — 트랜잭션 분리 (T1 → 토스 → T2)

**문제.** 승인을 트랜잭션 하나로 감싸면 T1에서 잡은 예매 행 락을 토스 응답(최대 60초)이 올 때까지 쥡니다. 그동안 같은 예매의 결제·취소 요청은 409로 바로 끝나지 않고 락을 기다리다 3초 뒤 예외로 끝납니다. 커넥션도 그만큼 묶이고, 토스가 승인한 뒤 커밋이 실패하면 승인 흔적까지 롤백됩니다.

**재현.** `ReservationPaymentConcurrencyTest.토스_호출_중에는_예매_행을_잠그지_않고_같은_예매의_다른_결제와_취소를_막는다`. 토스 호출을 래치로 붙잡은 채 같은 예매의 두 번째 결제와 취소를 보냅니다.

```diff
 @Service
 @RequiredArgsConstructor
+@Transactional
 public class ReservationPaymentService {
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **T1(검증 + 상태 `IN_PROGRESS` + 자원 확보) → 트랜잭션 밖에서 토스 → T2(확정/보상)** | **택합니다.** 락은 T1 동안만 쥡니다. 토스를 기다리는 동안 들어온 요청은 커밋된 `IN_PROGRESS`를 보고 즉시 409로 끝납니다 |
| 한 트랜잭션 | 가장 단순하지만 외부 호출 시간만큼 락·커넥션을 쥡니다 |
| 웹훅·비동기 확정 | 응답을 바로 줄 수 없고 이번 범위 밖입니다 |

**선택과 이유.** 락이 지키는 것은 "확인과 상태 변경 사이의 틈"이지 외부 호출이 아닙니다. 외부 호출 구간은 커밋해 둔 상태(7번의 `IN_PROGRESS`, 6번의 선점 연장)로 보호합니다. 순서를 잇는 `ReservationPaymentService`, `PurchasePaymentService`, `ReservationCancelService`, `PurchaseRefundService`에는 `@Transactional`을 붙이지 않습니다. 붙이면 T1·T2가 바깥 트랜잭션에 합류해 나눈 의미가 없어집니다.

**결과.**

| | 적용 전 (한 트랜잭션) | 적용 후 |
|---|---|---|
| 토스 대기 중 같은 예매의 두 번째 결제 | **3초 대기 후 `CannotAcquireLockException`** (Lock wait timeout exceeded) | 즉시 409 `PAYMENT_IN_PROGRESS` |
| 5회 반복 | 5/5 실패 | 5/5 통과 |

```
적용 전  Expecting actual throwable to be an instance of: CustomException
         but was: org.springframework.dao.CannotAcquireLockException: ... [Lock wait timeout exceeded; try restarting transaction]
         [select ... from reservation r1_0 where r1_0.reservation_id=? and r1_0.user_id=? for update of r1_0]
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 토스 대기 중 두 번째 결제가 락 대기 초과

![적용 전 — 토스 대기 중 두 번째 결제가 락 대기 초과](docs/images/concurrency/16-before-orchestrator-tx.png)

</details>

매점 승인·예매 취소·매점 환불의 오케스트레이터에 `@Transactional`을 붙이는 같은 변형도 결제 세션 2·4에서 확인했습니다. 모두 토스 대기 중의 다른 요청이 락 대기 예외로 끝났습니다.

#### 9. 결제된 예매 취소 — 예매 행 잠금 + 결제당 진행 중 취소 1건

**문제.** 취소 T1은 "남은 금액 확인 → 취소 요청 저장 → 토스 환불"입니다. 같은 결제의 부분 취소 두 건이 동시에 오면 각자 잔액을 보고 둘 다 토스를 부릅니다. 결과 불명 취소가 남은 상태에서 새 취소가 나가면, 첫 취소가 실제로는 성공했을 때 같은 금액이 한 번 더 환불될 수 있습니다.

**재현.**
- `ReservationCancelConcurrencyTest.토스_취소_중에는_예매_행을_잠그지_않고_같은_결제의_다른_취소를_막는다`: 토스 환불을 래치로 붙잡고 같은 결제의 다른 취소를 보냅니다.
- `ReservationCancelConcurrencyTest.같은_결제에_부분_취소_두_건이_동시에_와도_토스는_한_번만_불린다`: 두 건이 동시에 출발합니다. 늦은 T1은 앞선 T1 뒤에 줄을 서고, 토스 응답에 둔 200ms 동안 실행되므로 겹침이 보장됩니다.

```diff
 // ReservationService.startCancel — 행 잠금은 그대로 두고 진행 중 취소 확인만 뺀다
-        if (paymentCancelRepository.existsByPaymentIdAndStatus(payment.getId(), PaymentCancelStatus.IN_PROGRESS)) {
-            throw new CustomException(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
-        }
```

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **행 잠금 + 결제당 진행 중 취소 1건** | **택합니다.** 7번과 같은 구조입니다. 락이 T1끼리 줄을 세우고, `PaymentCancel(IN_PROGRESS)`가 토스 호출 구간을 덮습니다 |
| 행 잠금만 | 변형이 보여 주듯 막지 못합니다. 락은 T1 커밋과 함께 풀리고 토스 호출은 그 뒤입니다 |
| 토스 멱등키 | 부분 취소마다 다른 키를 씁니다. 같은 키를 쓰면 두 번째 부분 취소에 첫 응답이 캐시로 돌아와, 실제로는 취소되지 않은 채 성공으로 기록됩니다 |
| 잔액 검사만 (T1·T2 두 번) | 함께 넣었지만 T2 검사는 토스가 이미 환불한 뒤입니다. 토스 호출 전에 막는 것은 진행 중 취소 확인입니다 |

**선택과 이유.** 환불은 되돌릴 수 없는 외부 효과라 "토스를 부를지 말지"를 T1에서 결정해야 합니다. 그래서 진행 중 취소를 결제마다 하나로 제한했습니다. 좌석은 토스 환불이 확인된 T2에서 놓습니다. 먼저 놓으면 다른 회원이 가져가고, 환불이 거절됐을 때 되돌릴 수 없습니다(`토스가_환불하는_동안에는_취소할_좌석을_다른_회원이_가져가지_못한다`).

**결과.**

| | 적용 전 (진행 중 확인 없음) | 적용 후 |
|---|---|---|
| 토스 환불 대기 중 같은 결제의 다른 취소 | **받아들여짐** | 409 `PAYMENT_CANCEL_IN_PROGRESS` |
| 부분 취소 2건 동시 | **둘 다 성공** (토스 2번) | 1건 + `PAYMENT_CANCEL_IN_PROGRESS`, 토스 1번 |
| 5회 반복 | 각 5/5 실패 | 각 5/5 통과 |

```
적용 전  java.lang.AssertionError: Expecting code to raise a throwable.    ← 두 번째 취소가 거절되지 않음
적용 전  java.lang.AssertionError: Expected size: 1 but was: 2             ← 성공한 부분 취소 수
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 토스 환불 대기 중 다른 취소가 거절되지 않음

![적용 전 — 토스 환불 대기 중 다른 취소가 거절되지 않음](docs/images/concurrency/12-before-cancel-during-toss.png)

적용 전 — 동시 부분 취소 2건 모두 성공

![적용 전 — 동시 부분 취소 2건 모두 성공](docs/images/concurrency/13-before-cancel-concurrent.png)

</details>

#### 10. 매점 환불 — 구매 행 잠금 + 결제당 진행 중 취소 1건

**문제.** 매점은 회원이 취소할 수 없고, 매장(관리자)이 만들지 못한 수량만큼 환불합니다. 같은 구매에 수량 환불 두 건이 동시에 오면(관리자 두 명, 또는 버튼을 두 번 누름) 각자 남은 수량과 잔액을 보고 둘 다 토스를 부릅니다. 남은 수량은 **확정된 환불만** 세므로, 진행 중인 환불은 다른 요청의 계산에 드러나지 않습니다.

**재현.** `PurchaseRefundConcurrencyTest`의 두 테스트(토스 환불 대기 중 다른 환불 / 부분 환불 2건 동시). 변형은 `PurchaseService`에서 진행 중 취소 확인을 뺀 것입니다(9번과 같은 diff).

**고려한 방법.**

| 방법 | 판단 |
|---|---|
| **구매 행 잠금 + 결제당 진행 중 취소 1건** | **택합니다.** 잠금이 T1끼리 줄을 세우고, `PaymentCancel(IN_PROGRESS)`가 토스 호출 구간을 덮습니다 |
| 구매 행 잠금만 | 막지 못합니다(적용 전 실험). 잠금은 T1 커밋과 함께 풀리고 토스 호출은 그 뒤입니다 |
| 수량 조건부 UPDATE (`canceled_quantity + :n <= quantity`) | 확정 시점의 수량 초과만 막습니다. 토스 환불은 그보다 앞서 이미 나갔고, 결과 불명 환불과의 겹침도 막지 못합니다 |
| 토스 멱등키 | 환불 요청마다 다른 키를 쓰므로 두 번째 환불을 걸러내지 못합니다 |

**선택과 이유.** 9번과 같은 이유로 진행 중 취소를 결제마다 하나로 제한했습니다. 다른 점은 두 가지입니다. 관리자 API라 소유자 조건 없는 잠금 조회(`findByIdForUpdate`)를 쓰고, 이 조회도 트랜잭션의 첫 쿼리입니다. 그리고 환불해도 재고를 되돌리지 않으므로(만들 수 없는 상품이라 환불한 것이다), 예매처럼 "자원을 언제 놓는가"를 따질 것이 없고 막을 것은 이중 환불뿐입니다.

**결과.**

| | 적용 전 (진행 중 확인 없음) | 적용 후 |
|---|---|---|
| 토스 환불 대기 중 같은 구매의 다른 환불 | **받아들여짐** | 409 `PAYMENT_CANCEL_IN_PROGRESS` |
| 부분 환불 2건 동시 | **둘 다 성공** (토스 2번) | 1건만 |
| 5회 반복 | 각 5/5 실패 | 각 5/5 통과 |

```
적용 전  java.lang.AssertionError: Expected size: 1 but was: 2 in:
         [PurchaseResponse[..., remainingPrice=13000, ...], PurchaseResponse[..., remainingPrice=18000, ...]]
```

<details>
<summary>테스트 리포트 캡처</summary>

적용 전 — 토스 환불 대기 중 다른 환불이 거절되지 않음

![적용 전 — 토스 환불 대기 중 다른 환불이 거절되지 않음](docs/images/concurrency/14-before-refund-during-toss.png)

적용 전 — 동시 부분 환불 2건 모두 성공

![적용 전 — 동시 부분 환불 2건 모두 성공](docs/images/concurrency/15-before-refund-concurrent.png)

</details>

### 쓰지 않은 방법

| 방법 | 쓰지 않은 이유 |
|---|---|
| `synchronized` / `ReentrantLock` | 서버를 여러 대 띄우면 소용이 없고, `@Transactional`과 함께 쓰면 커밋 전에 락이 풀려 다음 스레드가 아직 커밋되지 않은 상태를 읽습니다. 모든 경합이 DB 데이터에서 일어나므로 DB가 막는 것이 맞습니다. 결제처럼 외부 호출을 끼면 JVM 락도 호출 시간만큼 쥐게 되어 8번의 문제가 그대로 생깁니다 |
| 격리 수준 `SERIALIZABLE` | 전역으로 느려지고 교착에 기대는 방식입니다. 문제는 격리 수준이 아니라 "확인한 값이 쓸 때까지 유지되는가"여서, 필요한 곳의 읽기만 잠금 읽기로 바꿨습니다 |
| 분산 락 (Redis 등) | DB가 하나라 모든 경합이 DB 안에서 해결됩니다. 인프라를 늘릴 이유가 없고, Redis는 이 프로젝트의 범위 밖입니다 |
| 큐 / 대기열 | 순간 트래픽을 흡수하는 수단입니다. 지금 문제는 트래픽 양이 아니라 정합성입니다 |

### 실행 결과

| 실험 | 적용한 변형 | 대상 테스트 × 5회 | 결과 |
|---|---|---|---|
| 깊게 다룬 지점, 적용 전 | 1·4(회원가입) 유니크 제약 제거, 3·7 `@Lock` 제거 | 게이트 테스트 4개 | **20/20 실패** |
| 얕게 다룬 지점, 적용 전 | 정렬 제거, 7 확인 제거, 6 연장 제거, 4(찜) 제약 제거, 5 `@Lock` 제거, 9·10 진행 중 확인 제거 | 9개 | **40/45 실패** (통과 5회는 정렬 제거, 1번 본문 참고) |
| 8, 적용 전 | 오케스트레이터에 `@Transactional` | 1개 | **5/5 실패** |
| 적용 후 | 없음 (반복 하네스만) | 위 대상 전부 | **69/69 통과** |

변형 실험이 끝날 때마다 원본으로 되돌리고 SHA-256이 실험 전과 같은지 확인했습니다. `./gradlew test` 전체 428개가 통과했습니다.

아래는 Gradle HTML 테스트 리포트(`build/reports/tests/test/index.html`)를 캡처한 것입니다. 캡처를 위해 위 실험을 한 번 더 돌렸고 결과는 처음과 같았습니다(좌석 정렬의 교착 횟수만 21건 → 7건으로 달랐습니다). 지점별 실패 메시지 캡처는 각 지점의 「결과」에 있습니다.

**깊게 다룬 지점, 적용 전 — 20/20 실패**

![깊게 다룬 지점 적용 전](docs/images/concurrency/01-before-deep-summary.png)

**얕게 다룬 지점, 적용 전 — 40/45 실패** (목록에 없는 `ReservationConcurrencyTest`가 통과한 5회, 정렬 제거)

![얕게 다룬 지점 적용 전](docs/images/concurrency/06-before-light-summary.png)

**적용 후 — 69/69 통과**

![적용 후](docs/images/concurrency/17-after-summary.png)

**전체 테스트 — 428/428 통과**

![전체 테스트](docs/images/concurrency/00-full-test.png)

## 중복 결제와 멱등성

결제 요청을 보냈는데 응답을 받지 못한 상황에서, 같은 요청이 다시 들어와도 돈과 좌석·재고가 올바른 상태로 남는지 정리했습니다. 이번에 새로 만든 코드는 없습니다. 결제 세션 1·2·4에서 승인·취소를 만들면서 넣어 둔 장치를 "같은 요청이 여러 번 온다"는 관점으로 다시 묶었습니다.

### 멱등성이란

**같은 요청을 한 번 보내든 여러 번 보내든 서버의 상태가 똑같이 남는 성질**입니다. 수학의 `f(f(x)) = f(x)`와 같은 뜻입니다.

- 기준은 **서버 상태**입니다. 응답은 달라도 됩니다. `DELETE /posts/1`을 두 번 보내면 두 번째는 404지만, 글이 지워진 상태는 같으므로 멱등합니다.
- HTTP 메서드 중 `GET`, `PUT`, `DELETE`는 멱등하도록 정의되어 있고 `POST`는 아닙니다. 결제 승인·취소는 모두 `POST`라서 **멱등성을 서버가 따로 만들어야 합니다.**

| 연산 | 멱등한가 | 이유 |
|---|---|---|
| 잔액을 10,000원으로 설정 | 예 | 몇 번을 해도 10,000원 |
| 잔액에서 10,000원 차감 | **아니오** | 할 때마다 줄어듦 |
| 주문 `A`를 결제 완료 상태로 | 예 | 이미 완료면 그대로 |
| 주문 `A`의 금액을 결제 | **아니오** | 할 때마다 돈이 나감 |

### 결제에서 멱등성이 중요한 이유

**응답을 못 받은 쪽은 요청이 처리됐는지 알 수 없습니다.** 타임아웃에는 세 가지 경우가 섞여 있습니다.

1. 요청이 서버에 닿지 않았다 → 결제 안 됨
2. 서버가 처리하던 중에 끊겼다 → 결제됐을 수도, 안 됐을 수도 있음
3. 처리는 끝났는데 응답만 사라졌다 → **결제됨**

클라이언트가 할 수 있는 일은 다시 보내는 것뿐인데, 요청이 멱등하지 않으면 3번 경우에 **이중 결제**가 됩니다. 반대로 실패로 단정하고 멈추면 돈은 빠졌는데 주문은 없는 상태가 됩니다. 결제를 되돌리려면 환불이라는 또 다른 외부 동작이 필요해서, "일단 하고 나중에 고친다"가 안 됩니다. 그래서 **"다시 보내도 안전하다"를 보장하는 것이 결제 재시도의 전제**입니다.

이 서비스에는 같은 요청이 여러 번 오는 길이 두 개 있습니다.

| 경계 | 같은 요청이 다시 오는 경우 |
|---|---|
| 브라우저 → 우리 서버 | 결제 버튼을 두 번 누름, 새로고침, 응답을 못 받은 클라이언트의 재전송, 두 탭에서 각각 결제 |
| 우리 서버 → 토스 | 승인·취소 응답이 타임아웃으로 끊긴 뒤 같은 요청을 다시 보냄 |

### 멱등성을 만드는 방법

| 방법 | 원리 | 쓰는 곳 |
|---|---|---|
| **멱등키** | 요청마다 고유한 키를 붙이고, 서버가 키별로 처리 결과를 저장해 같은 키가 다시 오면 저장한 결과를 돌려줍니다 | 외부 결제 API(토스, Stripe) |
| **상태 전이 검사** | "대기 → 완료"처럼 한 방향으로만 바뀌게 하고, 이미 바뀐 상태면 다시 실행하지 않습니다 | 주문·결제 상태 |
| **유니크 제약** | 같은 자연 키(주문번호 등)로 두 번째 행이 생기지 않게 DB가 막습니다 | 생성 요청 |
| **절대값으로 쓰기** | "차감" 대신 "이 값으로 설정"처럼 결과를 직접 지정합니다 | `PUT` |

### Toss Payments의 멱등성 처리

[멱등키 문서](https://docs.tosspayments.com/reference/using-api/idempotency-key)와 실제 테스트 호출로 확인한 내용입니다(`docs/toss-payments-guide.md`).

- 모든 `POST` 요청에 **`Idempotency-Key` 헤더**를 붙일 수 있습니다. `GET`에는 무시됩니다.
- 같은 키로 다시 보내면 **다시 처리하지 않고 첫 응답을 그대로** 돌려줍니다.
- 첫 요청이 아직 처리 중일 때 같은 키가 오면 **409 `IDEMPOTENT_REQUEST_PROCESSING`** 입니다.
- 키는 처음 쓴 날부터 **15일** 유효하고 300자까지입니다. 15일이 지나면 같은 키도 새 요청으로 처리됩니다.
- 이미 승인된 주문의 승인이 다시 오면 `ALREADY_PROCESSED_PAYMENT`(이미 처리된 결제)가 옵니다. 이 응답은 **앞선 승인이 성공했다는 뜻**이라 거절로 처리하면 안 됩니다.

### 이 서비스에 적용한 것

**두 겹으로 막습니다.** 우리 서버 입구에서는 **상태 전이 검사 + 행 잠금**으로 같은 요청이 토스까지 두 번 가지 않게 하고, 토스로 나가는 호출에는 **멱등키**를 붙여 우리가 다시 보내도 토스에서 두 번 처리되지 않게 합니다.

#### 멱등키 설계 — 승인은 주문번호, 취소는 요청마다 새 키

| | 승인 | 취소 |
|---|---|---|
| 멱등키 | `orderId` | `PaymentCancel`마다 새 UUID |
| 저장 시점 | 결제 주문 생성 시 (`Payment.orderId`) | 취소 요청 T1에서 **토스를 부르기 전에** (`PaymentCancel.idempotencyKey`) |
| 이유 | `orderId`는 결제 시도마다 서버가 새로 만들고 재사용하지 않으므로 "같은 승인 = 같은 `orderId`"가 정확히 맞습니다 | 한 결제에 부분 취소가 여러 번 있을 수 있습니다. `orderId`를 키로 쓰면 두 번째 부분 취소에 첫 취소의 응답이 돌아와, **실제로는 취소되지 않았는데 성공으로 기록됩니다** |

취소 키를 토스 호출 전에 DB에 저장하는 것이 핵심입니다. 결과를 모르는 취소를 다시 보낼 때 **처음과 같은 키·금액·사유**를 써야 토스가 같은 요청으로 보는데, 키를 메모리에만 두면 재시도할 때 새 키가 만들어지고 같은 금액이 한 번 더 환불됩니다.

#### 같은 결제 승인 요청이 여러 번 오는 경우

| 상황 | 막는 장치 | 결과 | 테스트 |
|---|---|---|---|
| 첫 승인이 토스를 기다리는 중에 같은 `orderId`로 다시 승인 | 예매(구매) 행 잠금 → `Payment`가 `IN_PROGRESS` | 409 `PAYMENT_IN_PROGRESS`, 토스 호출 없음 | `PaymentTest.승인_중인_결제를_다시_시작하면_PAYMENT_IN_PROGRESS`, `ReservationPaymentConcurrencyTest.토스_호출_중에는_예매_행을_잠그지_않고_같은_예매의_다른_결제와_취소를_막는다` |
| 승인이 끝난 뒤 같은 `orderId`로 다시 승인 (응답을 놓친 클라이언트) | 예매가 이미 `RESERVED` | 409 `RESERVATION_NOT_PENDING`, **토스 1번** | `ReservationControllerTest.확정된_예매를_다시_승인하면_409이고_토스는_한_번만_불린다` |
| 거절된 뒤 같은 `orderId`로 다시 승인 | `Payment`가 `FAILED` (`READY`에서만 승인 시작) | 409 `INVALID_PAYMENT_STATUS` / 매점 `PURCHASE_NOT_PENDING`. 다시 결제하려면 새 주문 | `ReservationServiceTest.거절된_결제를_다시_승인하면_INVALID_PAYMENT_STATUS`, `PurchaseControllerTest.거절되면_402이고_재고가_돌아오며_같은_주문으로는_다시_결제할_수_없다` |
| 두 탭에서 같은 예매를 각각 결제 (`orderId`가 둘) | 예매 행 잠금 + 예매당 승인 중 결제 1건 | 1건만 승인. **멱등키로는 못 막는 경우** | `ReservationPaymentConcurrencyTest.같은_예매의_두_주문을_동시에_승인해도_토스는_한_번만_불린다` |
| 토스 응답이 타임아웃·5xx | 실패로 단정하지 않고 `IN_PROGRESS`로 남김 | 504 `PAYMENT_RESULT_UNKNOWN`. 같은 예매의 새 결제·취소가 막혀 이중 결제가 생기지 않음. 2분 뒤 스케줄러가 토스 조회로 정리 | `ReservationControllerTest.결과를_모르면_504이고_결제는_승인중으로_남아_취소도_새_결제도_막힌다` |
| 토스가 `ALREADY_PROCESSED_PAYMENT` | 거절이 아니라 결과 불명으로 분류 | 위와 같음 | `TossPaymentsClientTest.이미_승인된_결제라는_응답은_거절이_아니라_결과_불명이다` |

승인 요청의 `orderId` 멱등키는 연결 실패 재시도의 안전망입니다. 연결 거부·연결 타임아웃이면 같은 키로 한 번만 다시 보내는데, 첫 요청이 혹시 토스에 닿았더라도 두 번 결제되지 않습니다. 읽기 타임아웃 뒤에는 승인을 다시 보내지 않고 조회로 맞춥니다(「외부 API 통신과 예외 처리」).

#### 같은 결제 취소 요청이 여러 번 오는 경우

| 상황 | 막는 장치 | 결과 | 테스트 |
|---|---|---|---|
| 첫 취소가 토스를 기다리는 중에 다시 취소 | 예매(구매) 행 잠금 + 결제당 진행 중 취소 1건 | 409 `PAYMENT_CANCEL_IN_PROGRESS` | `ReservationCancelConcurrencyTest.토스_취소_중에는_예매_행을_잠그지_않고_같은_결제의_다른_취소를_막는다`, `PurchaseRefundConcurrencyTest` |
| 전체 취소가 끝난 뒤 다시 취소 | 예매가 이미 `CANCELLED` | 409 `ALREADY_CANCELLED`, **토스 1번** | `ReservationControllerTest.전체_취소된_예매를_다시_취소하면_409이고_토스를_부르지_않는다` |
| 부분 취소한 좌석을 다시 취소 | 좌석이 이미 취소됨 | 409 `SEAT_ALREADY_CANCELLED`, **토스 1번** | `ReservationControllerTest.이미_취소된_좌석을_다시_취소하면_409이고_토스를_부르지_않는다` |
| 결과를 모르는 취소를 재시도 (`POST …/cancellations/{cancelId}/retry`) | **저장해 둔 같은 멱등키·금액·사유**로 다시 보냄 | 토스가 이미 처리했으면 첫 응답이 와서 한 번만 환불 | `ReservationControllerTest.취소_결과를_모르면_504이고_좌석을_놓지_않으며_같은_멱등키로_재시도하면_반영된다`, `PurchaseAdminControllerTest.결과를_모르면_504이고_새_환불은_막히며_같은_멱등키로_재시도하면_반영된다` |
| 재시도와 첫 요청이 겹쳐 완료 처리(T2)가 두 번 옴 | `PaymentCancel`이 이미 `DONE`이면 아무것도 바꾸지 않음 | 누적 취소액·좌석이 두 번 반영되지 않음 | `ReservationServiceTest.이미_반영된_취소의_완료가_다시_와도_두_번_반영하지_않는다` |
| 결과가 정해진 취소를 재시도 / 요청 후 14일이 지난 취소를 재시도 | `PaymentCancel.verifyRetryable` | 409 `PAYMENT_CANCEL_NOT_RETRYABLE` / `PAYMENT_CANCEL_RETRY_EXPIRED` | `ReservationControllerTest.결과가_정해진_취소는_재시도할_수_없다`, `ReservationControllerTest.요청_후_14일이_지난_취소는_재시도하지_않는다` |

- **재시도는 14일까지만 받습니다.** 토스 멱등키가 15일 유효라, 그 뒤에 같은 키로 보내면 새 요청이 되어 두 번 환불될 수 있습니다. 남은 하루는 여유입니다.
- **애매한 응답은 거절로 처리하지 않습니다.** `ALREADY_CANCELED_PAYMENT`, `IDEMPOTENT_REQUEST_PROCESSING`, `PROVIDER_ERROR`, 5xx, 타임아웃은 결과 불명입니다. 거절로 처리하면 회원이 다시 취소를 요청하고, 그 요청은 **새 키**를 받으므로 같은 금액이 한 번 더 환불됩니다. 부분 취소는 잔액이 남아 있어 토스도 이 요청을 막지 않습니다.
- **결과 불명인 동안 좌석을 놓지 않습니다.** 실제로는 환불되지 않았는데 좌석을 놓으면 회원은 돈과 좌석을 모두 잃습니다.

#### 한계 — 응답까지 같지는 않습니다

승인이 끝난 뒤 같은 승인이 다시 오면 **상태는 그대로지만 응답은 첫 번째(200)와 다른 409**입니다. 토스처럼 키별로 첫 응답을 저장해 두었다가 돌려주는 방식이 아니라 상태 검사로 막기 때문입니다. 데이터는 올바르게 남고 토스도 한 번만 불리지만, 응답을 놓친 클라이언트는 409를 받은 뒤 `GET /api/reservations/{id}`나 결제 조회로 결과를 확인해야 합니다.

클라이언트가 직접 `Idempotency-Key`를 보내고 서버가 응답을 저장하는 방식도 생각했지만 넣지 않았습니다. 승인에는 서버가 발급한 `orderId`가 이미 같은 역할을 하고, 취소는 상태(이미 취소됨, 진행 중 취소 있음)로 걸러집니다. 응답 저장 테이블을 두어 얻는 것은 "409 대신 200"뿐입니다.

### 동시성 제어와 멱등성

둘 다 "같은 데이터에 요청이 여러 번 닿는다"는 데서 출발해서 비슷해 보이지만, 묻는 질문이 다릅니다.

- **동시성 제어는 "겹쳐도 괜찮은가"를 묻습니다.** 서로 다른 요청이 **같은 시간에** 같은 데이터를 읽고 쓰면서 실행 순서가 섞이는 문제를 막습니다. 원인은 요청마다 스레드와 트랜잭션이 따로 돈다는 데 있고, 해결은 락·유니크 제약·조건부 UPDATE로 "하나씩 실행한 것과 같은 결과"를 만드는 것입니다.
- **멱등성은 "여러 번 와도 괜찮은가"를 묻습니다.** **같은 요청이** 여러 번 도착해도 한 번 처리한 것과 같은 결과를 만드는 성질입니다. 원인은 네트워크가 응답을 잃어 보낸 쪽이 결과를 모른다는 데 있고, 해결은 멱등키나 상태 전이로 "이미 한 일은 다시 하지 않는 것"입니다.

| | 동시성 제어 | 멱등성 |
|---|---|---|
| 막는 것 | 실행 순서가 섞여서 생기는 잘못된 결과 (갱신 손실, 중복 생성, 이중 승인) | 재전송 때문에 같은 동작이 두 번 실행되는 것 (이중 결제, 이중 환불) |
| 요청의 관계 | **다른** 요청일 수도, 같은 요청일 수도 | **같은** 요청 |
| 시간 | **겹칠 때** 문제 | 겹치지 않고 **차례로** 와도 문제 |
| 원인 | 다중 스레드·트랜잭션 | 응답 유실, 재클릭, 재시도 |
| 어디서 막나 | 우리 DB 안 (락이 닿는 곳까지) | 시스템 경계를 넘는 호출 (브라우저→서버, 서버→토스) |
| 수단 | 비관적 락, 유니크 제약, 조건부 UPDATE | 멱등키, 상태 전이 검사, 자연 키 |

**하나로 다른 하나를 대신할 수 없습니다.** 이 서비스에서 실제로 부딪힌 경우가 양쪽에 다 있습니다.

- **락만으로는 못 막는 경우 — 결과 불명 취소의 재시도.** 첫 취소는 이미 끝났고 재시도는 몇 분 뒤에 옵니다. 겹치지 않으니 락은 할 일이 없습니다. 재시도에 새 키를 쓰면 락이 아무리 촘촘해도 같은 금액이 두 번 환불됩니다. 막는 것은 **저장해 둔 같은 멱등키**뿐입니다. 토스 안에서 일어나는 일이라 우리 락이 닿지도 않습니다.
- **멱등키만으로는 못 막는 경우 — 두 탭에서 같은 예매 결제.** 두 탭은 결제 시도마다 다른 `orderId`를 받으므로 토스가 보기에는 서로 다른 요청입니다. 멱등키로는 둘 다 통과합니다. 막는 것은 **예매 행 잠금 + 예매당 승인 중 결제 1건**이라는 동시성 제어입니다(「동시성 문제」 7번).

그리고 **멱등성을 구현하는 코드 자체가 동시성 문제를 품고 있습니다.** "이 요청을 이미 처리했나?"를 확인하고 처리하는 것은 그대로 **확인 후 실행(check-then-act)** 이라, 같은 요청 두 개가 동시에 오면 둘 다 "아직 안 함"을 볼 수 있습니다. 토스가 처리 중인 키에 409 `IDEMPOTENT_REQUEST_PROCESSING`을 주는 것도, 우리 서버가 같은 `orderId`의 승인 두 건을 예매 행 잠금으로 줄 세운 뒤 `IN_PROGRESS`를 보고 거르는 것도 이 틈을 막기 위해서입니다.

정리하면 **동시성 제어는 "동시에"의 문제를, 멱등성은 "다시"의 문제를 풉니다.** 결제처럼 둘 다 일어나는 곳에서는 멱등성이 동시성 제어 위에 서 있습니다.

## 외부 API 통신과 예외 처리

결제는 토스페이먼츠 API를 부릅니다. 이 절은 Spring에서 외부 API를 부르는 방법을 비교해 이 프로젝트가 고른 방법을 설명하고, **우리가 관리하지 않는 서버**를 부를 때 생기는 실패를 어떻게 다루는지 정리합니다.

### Spring에서 외부 API를 호출하는 방법

| | RestTemplate | **RestClient** | WebClient | OpenFeign | HTTP Interface (`@HttpExchange`) |
|---|---|---|---|---|---|
| 등장 | Spring 3 (2009) | Spring 6.1 (2023) | Spring 5 (2017) | Netflix → Spring Cloud | Spring 6 (2022) |
| 호출 방식 | 동기·블로킹 | 동기·블로킹 | 비동기·논블로킹 (`Mono`/`Flux`) | 동기 | 백엔드에 따라 동기·비동기 |
| 코드 모양 | 메서드 하나에 인자 여러 개 (`exchange(url, method, entity, type)`) | 체이닝 (`post().uri().body().retrieve()`) | 체이닝 + Reactor 연산자 | 인터페이스 + 애너테이션 | 인터페이스 + 애너테이션 |
| 필요한 의존성 | spring-web | spring-web | spring-webflux + Reactor Netty | spring-cloud-starter-openfeign + Spring Cloud BOM | spring-web (백엔드로 RestClient 또는 WebClient) |
| 오류 처리 | 전역 `ResponseErrorHandler` | 호출마다 `onStatus`, 또는 `HttpClientErrorException` / `HttpServerErrorException` / `ResourceAccessException` | `onStatus` + `onErrorResume` 등 Reactor 연산자 | 전역 `ErrorDecoder`, `FeignException` | 백엔드 클라이언트의 방식 |
| 테스트 | `MockRestServiceServer` | `MockRestServiceServer` | MockWebServer 등 별도 서버 | WireMock 등 별도 서버 | 백엔드를 따름 |
| 현재 위치 | 유지보수 모드. Spring 팀이 7.x에서 deprecated, 8.0에서 제거 예정이라고 발표 | Spring이 권하는 동기 클라이언트 | 리액티브 애플리케이션의 표준 | Spring Cloud OpenFeign은 기능 추가를 멈추고 HTTP Interface를 대안으로 안내 | Feign의 공식 대안 |

**RestTemplate** — 가장 오래 쓰인 동기 클라이언트입니다. 쓰는 곳이 많아 자료가 풍부하지만 API가 메서드 오버로딩 위주라 헤더 하나를 붙이려 해도 `HttpEntity`를 만들어야 합니다. 새 기능은 들어오지 않습니다.

**RestClient** — RestTemplate과 같은 기반(`ClientHttpRequestFactory`, `HttpMessageConverter`) 위에 WebClient 같은 체이닝 API를 얹은 동기 클라이언트입니다. 의존성 추가가 없고, 요청마다 헤더·상태 코드 처리를 호출 코드에 바로 보이게 쓸 수 있습니다. 엔드포인트가 많아지면 비슷한 호출 코드가 반복됩니다.

**WebClient** — 논블로킹 클라이언트입니다. 적은 스레드로 많은 호출을 동시에 보내거나 스트리밍할 때 강합니다. 대신 webflux 의존성이 들고, Spring MVC + JPA처럼 요청 스레드가 블로킹되는 애플리케이션에서는 결국 `.block()`으로 결과를 기다려야 해서 장점이 사라집니다. 오류 처리도 Reactor 연산자로 해야 해서 익히는 비용이 있습니다.

**OpenFeign** — 인터페이스에 `@FeignClient`와 매핑 애너테이션만 달면 구현을 만들어 줍니다. 서비스끼리 많이 부르는 MSA에서 코드가 짧아집니다. 대신 Spring Cloud 의존성과 BOM 버전을 Boot와 맞춰야 하고, 오류 변환이 `ErrorDecoder` 한 곳에 모여 "이 API의 이 응답이 무슨 뜻인가"가 호출 코드에서 보이지 않습니다.

**HTTP Interface** — Feign과 같은 선언형 방식을 Spring이 기본으로 제공합니다. 의존성이 필요 없고 백엔드로 RestClient나 WebClient를 고를 수 있습니다. 선언형을 원한다면 지금은 Feign보다 이쪽이 맞습니다.

### 이 프로젝트의 선택 — RestClient

`global/config/TossPaymentsConfig`가 토스 전용 `RestClient` 빈을 만들고, `global/client/TossPaymentsClient`가 그 빈으로 승인·취소·조회를 부릅니다. 고른 이유는 지금 프로젝트의 상황에서 나옵니다.

1. **결제 흐름이 동기여야 합니다.** 승인은 T1(검증·승인 중 표시) → 토스 호출 → T2(확정 또는 보상)입니다. 토스의 답을 받아야 T2에서 좌석을 확정할지 정할 수 있고, 회원에게도 그 결과를 응답으로 돌려줘야 합니다. 비동기 클라이언트를 써도 결국 기다려야 해서 얻는 것이 없습니다.
2. **애플리케이션이 Spring MVC + JPA입니다.** 요청 스레드가 블로킹되는 구조라 WebClient의 논블로킹 장점이 살지 않고 webflux 의존성만 늘어납니다.
3. **부르는 API가 넷뿐이고, 오류의 의미가 API마다 다릅니다.** 같은 4xx라도 승인의 `ALREADY_PROCESSED_PAYMENT`, 취소의 `ALREADY_CANCELED_PAYMENT`, 조회의 `NOT_FOUND_PAYMENT`만 뜻이 다릅니다. 연결 실패도 승인에서는 "돈이 안 빠졌다"이고 취소에서는 "모른다"입니다(아래). 이 판단을 호출 바로 옆에 두는 것이 읽기 쉽습니다. Feign의 `ErrorDecoder`나 전역 `ResponseErrorHandler`로 모으면 API별 판단이 한 곳에 섞입니다. 선언형으로 줄일 코드도 많지 않습니다.
4. **의존성을 늘리지 않고 기존 테스트 도구를 그대로 씁니다.** spring-web에 들어 있고, `MockRestServiceServer`로 토스 응답(4xx 본문, 5xx, 타임아웃, 연결 실패)을 흉내 낼 수 있습니다.
5. **RestTemplate은 새 코드에 쓸 이유가 없습니다.** 같은 기반에 더 나은 API를 얹은 것이 RestClient입니다.

부르는 API가 늘어나 선언형이 필요해지면, 클라이언트를 바꾸지 않고 RestClient를 백엔드로 하는 HTTP Interface로 옮길 수 있습니다.

| 설정 | 값 | 이유 |
|---|---|---|
| 연결 타임아웃 | 5초 | 연결조차 안 되는 상황을 오래 기다리지 않습니다 |
| 읽기 타임아웃 | 60초 | 토스가 일부 API는 60초까지 걸릴 수 있다며 최소 60초를 권합니다. 짧게 잡으면 토스는 승인했는데 우리는 실패로 처리하는 불일치가 늘어납니다 |
| 인증 | `Authorization: Basic base64(시크릿키 + ":")`를 빈의 기본 헤더로 | 호출 코드가 인증을 신경 쓰지 않게 합니다 |
| 멱등키 | 승인은 `orderId`, 취소는 취소 요청마다 저장해 둔 UUID | 같은 요청을 다시 보내도 토스에서 두 번 처리되지 않습니다(「중복 결제와 멱등성」) |

**클라이언트가 하는 일과 하지 않는 일.** `TossPaymentsClient`는 응답을 **성공 / 거절 / 결과 모름** 셋으로 나누는 데까지만 합니다. 거절됐을 때 좌석을 둘지, 재고를 되돌릴지는 예매·매점 서비스가 정합니다. 클라이언트에서 정하면 서로 다른 두 도메인의 보상 규칙이 `global`로 새어 들어옵니다.

### 실패를 하나로 두지 않는다 — 성공 / 거절 / 결과 모름

외부 API 호출의 실패는 두 종류입니다.

- **거절** — 토스가 "처리하지 않았다"고 분명히 답했습니다. 돈이 빠지지 않았으므로 우리도 실패로 확정하고 보상(재고 복구 등)해도 됩니다.
- **결과 모름** — 응답이 끊겼거나 토스 쪽이 오류를 냈습니다. 토스에서는 승인됐을 수도 있습니다.

둘을 "실패" 하나로 합치면 결과 모름을 거절처럼 처리하게 됩니다. 그러면 **돈은 빠졌는데 결제 실패로 기록**되고, 회원이 다시 결제해 두 번 빠지기도 합니다. 그래서 결과를 sealed interface(`TossConfirmResult` / `TossCancelResult` / `TossLookupResult`)로 나누고, 서비스는 `switch`로 모든 경우를 처리해야 컴파일됩니다.

| 토스 응답 | 승인 | 취소 | 조회 |
|---|---|---|---|
| 2xx + 요청과 일치하는 정상 본문 | 승인 | 취소됨 | 찾음 |
| 2xx인데 다른 결제의 응답 / 금액 불일치 / 본문 없음 / JSON이 아님 / `DONE`이 아님 | **결과 모름** | **결과 모름** | 조회 불가 |
| 카드사 거절 등 일반 4xx | 거절 (402) | 거절 | 조회 불가 (`NOT_FOUND_PAYMENT`만 "없음") |
| `ALREADY_PROCESSED_PAYMENT`, `ALREADY_CANCELED_PAYMENT` | 결과 모름 | 결과 모름 | |
| 400 `PROVIDER_ERROR` ("일시적인 오류") | 결과 모름 | 결과 모름 | |
| 409 `IDEMPOTENT_REQUEST_PROCESSING` | | 결과 모름 | |
| 401 `UNAUTHORIZED_KEY`, `INVALID_API_KEY`, 429 | 거절 + `serviceError` (503) | **결과 모름** | 조회 불가 |
| 연결 실패 (연결 거부·연결 타임아웃·DNS) | 같은 멱등키로 1번 다시 → 또 실패면 거절 + `serviceError` (503) | 1번 다시 → 또 실패면 **결과 모름** | 1번 다시 → 또 실패면 조회 불가 |
| 읽기 타임아웃, 5xx | 결과 모름 (504) | 결과 모름 | 조회 불가 |

### 상황별 동작

#### 1. Toss Payments API 호출에 실패한다면

| 상황 | 동작 | 회원이 받는 응답 |
|---|---|---|
| **연결이 안 됨** | 요청이 토스에 닿지 않은 것이 확실하므로 **같은 멱등키로 한 번만** 다시 보냅니다. 혹시 닿았더라도 멱등키 때문에 두 번 처리되지 않습니다 | 재시도가 성공하면 정상 응답 |
| 다시 보내도 연결이 안 됨 (승인) | 결제를 실패로 확정합니다. 예매는 좌석을 유지하고 매점은 재고를 되돌립니다. 새 주문으로 다시 결제할 수 있습니다 | **503** `PAYMENT_SERVICE_UNAVAILABLE` "잠시 후 다시 시도해 주세요" |
| 우리 키가 틀림 (401·`INVALID_API_KEY`) / 호출 한도 초과 (429) | 토스가 결제를 판단하지 않았으므로 돈은 빠지지 않았습니다. 카드사 거절과 똑같이 보상하되, **카드를 바꿔도 소용없으므로** 안내를 다르게 하고 error 로그를 남깁니다 | **503** (카드사 거절의 402 "다른 결제수단으로"와 구분) |
| 카드사 거절 | 결제 실패. 예매는 좌석을 유지해 선점 시간 안에 다시 결제할 수 있습니다 | 402 `PAYMENT_REJECTED` |
| **읽기 타임아웃 / 5xx / `PROVIDER_ERROR`** | 실패로 단정하지 않습니다. 결제를 `IN_PROGRESS`로 남겨 같은 예매의 새 결제·취소를 막고, 아래 「결과 불명 정리」가 토스 조회로 맞춥니다. 읽기 타임아웃은 **다시 보내지 않습니다.** 이미 60초를 기다렸고, 토스가 처리 중이라면 다시 보내도 결과를 더 빨리 알 수 없습니다 | **504** `PAYMENT_RESULT_UNKNOWN` "잠시 후 내역에서 확인해 주세요" |

**연결 실패를 승인과 취소에서 다르게 다루는 이유.** 승인은 주문번호마다 한 번만 보냅니다(같은 주문의 재승인은 T1이 막습니다). 그래서 연결이 안 됐다면 이 주문의 승인은 토스에 **한 번도 닿지 않은 것**이고, 거절로 확정해도 안전합니다. 취소는 결과를 모르는 취소를 **같은 멱등키로 다시 보내는 재시도**일 수 있습니다. 재시도가 연결에 실패했다고 첫 요청도 실패했다는 뜻은 아닙니다. 첫 요청이 이미 환불됐을 수 있는데 거절로 처리하면, 회원이 다시 취소를 요청할 때 **새 키**로 같은 금액이 한 번 더 환불됩니다. 키 오류(401)도 같은 이유로 취소에서는 결과 모름입니다.

#### 2. Toss에서는 결제가 성공했지만 우리 서버에서 처리 중 오류가 발생한다면

토스 승인 뒤 T2(결제 `DONE` + 예매 `RESERVED`)에서 DB 오류가 나면 T2가 롤백되고 결제는 `IN_PROGRESS`로 남습니다. 서버가 T2 전에 죽어도 같은 모습으로 남습니다.

- 회원에게 500을 주지 않습니다. 500이면 회원은 결제가 안 된 줄 알고 다시 결제합니다. 오케스트레이터(`ReservationPaymentService`)가 예외를 잡아 **504 "결제 결과를 확인하지 못했습니다"** 로 바꾸고, `orderId`·`paymentKey`를 error 로그로 남깁니다.
- 결제가 승인 중으로 남아 있어 같은 예매의 새 결제와 취소가 막힙니다. 이중 결제가 생기지 않습니다.
- **승인을 시작할 때(T1) `paymentKey`를 먼저 저장합니다.** 예전에는 승인에 성공해야 저장했는데, 문서 공용 테스트 키는 `orderId`로 조회가 안 되므로 결과를 모르는 결제를 토스에 물어볼 방법이 없었습니다.

**결과 불명 정리.** 스케줄러가 1분마다, 관리자는 즉시(`POST /api/admin/reservations/payments/{orderId}/recovery`, 매점은 `/api/admin/purchases/payments/{orderId}/recovery`) 정리합니다.

```
승인을 시작한 지 2분이 지난 IN_PROGRESS 결제 (그 전이면 원래 요청이 아직 토스를 기다릴 수 있어 409)
  → 토스에 paymentKey로 조회
     DONE + 주문번호·금액 일치 ─ 예매 행 잠금 ─┬─ 예매가 아직 PENDING → 결제 DONE + 예매 확정
                                              └─ 예매가 EXPIRED(좌석을 잃음) → 결제 DONE + 전액 환불 요청을 같은 트랜잭션에 저장 → 토스 취소
     ABORTED / EXPIRED / NOT_FOUND_PAYMENT ─ 결제 FAILED (매점은 재고 복구), 새 주문 가능
     READY / IN_PROGRESS / 조회 불가 / 금액 불일치 ─ 보류. 다음 회차에 다시 묻는다
  → 이어서 요청한 지 2분이 지난 결과 불명 취소(회원 취소·매장 환불·자동 환불)를 저장해 둔 멱등키로 다시 보낸다
```

| 판단 | 이유 |
|---|---|
| 2분을 기다린다 | 토스 호출은 최대 연결 5초 × 2 + 읽기 60초입니다. 원래 요청의 T2와 정리가 함께 결제를 확정하지 않게 합니다. 겹치더라도 둘 다 예매(구매) 행을 먼저 잠그고 결제가 아직 `IN_PROGRESS`인지 다시 확인하므로 한 번만 반영됩니다 |
| 만료 시각이 지났어도 `PENDING`이면 확정한다 | 만료된 선점의 좌석은 다른 회원의 선점 요청이 예매를 `PENDING → EXPIRED`로 바꿀 때 함께 풀립니다. 그래서 `PENDING`이면 좌석은 아직 이 예매의 것이고, 돈을 낸 회원에게는 환불보다 확정이 맞습니다(`Reservation.confirmAfterRecovery`) |
| 좌석을 잃었으면 환불하고 예매는 `EXPIRED`로 둔다 | 회원이 취소한 것이 아니라 선점이 만료된 것입니다. `CANCELLED`로 바꾸면 좌석이 왜 풀렸는지 되짚을 수 없습니다. 환불 사실은 결제(`CANCELED`, 사유 "좌석 선점 만료로 자동 환불")에 남습니다 |
| 승인 확정과 환불 요청을 한 트랜잭션에 저장한다 | 토스 취소 전에 서버가 죽어도 남은 취소 요청을 다음 회차에 같은 멱등키로 마칠 수 있습니다 |
| 토스가 `IN_PROGRESS`(인증만 됨)면 기다린다 | 아직 승인될 수 있습니다. 인증 후 10분이 지나면 토스가 `EXPIRED`로 바꾸므로 그때 실패로 확정합니다 |
| 조회 실패를 "결제 없음"으로 읽지 않는다 | `NOT_FOUND_PAYMENT`만 없다는 뜻입니다. 5xx·타임아웃·`NOT_FOUND_MERCHANT`를 없음으로 읽으면 승인된 결제를 실패로 확정합니다 |
| 매점은 결과를 모르는 동안 재고를 되돌리지 않는다 | 되돌렸는데 승인된 것으로 밝혀지면 판 수량보다 재고가 많아집니다 |

취소에서도 같은 일이 생길 수 있습니다. 토스가 환불한 뒤 T2가 실패하면 취소 요청이 `IN_PROGRESS`로 남고 504를 줍니다. 좌석은 놓지 않습니다. 회원이 재시도하거나 스케줄러가 **같은 멱등키**로 다시 보내면 토스가 첫 응답을 돌려줘 그때 반영됩니다.

#### 3. 외부 API가 예상하지 못한 응답을 반환한다면

| 응답 | 동작 | 이유 |
|---|---|---|
| 2xx인데 `orderId`·`paymentKey`·금액이 요청과 다름 | 결과 모름 + error 로그 | 다른 결제의 응답으로 확정하면 받은 돈과 기록이 어긋납니다. 돈은 움직였을 수 있으므로 거절도 아닙니다 |
| 2xx인데 본문이 없거나 JSON이 아님 (프록시의 HTML 등) | 결과 모름 | 토스가 처리했는지 알 수 없습니다 |
| 2xx인데 상태가 `DONE`이 아님 (가상계좌 `WAITING_FOR_DEPOSIT`) | 결과 모름 | 결과가 나중에 정해지는 결제입니다 |
| 취소 응답의 마지막 거래 금액이 요청과 다름 / 거래 상태가 `DONE`이 아님 | 결과 모름 | 그 거래로 확정하면 누적 취소액과 좌석·수량이 실제 환불과 어긋납니다 |
| 조회 응답이 물어본 결제가 아님 | 조회 불가 | 엉뚱한 결제로 정리하지 않습니다 |
| 4xx인데 본문을 해석할 수 없음 | 거절, 코드 `HTTP_400` 등 | 형식이 어긋나도 토스가 요청을 받아들이지 않았다는 사실은 같습니다 |
| 모르는 필드가 추가됨 | 무시 (`@JsonIgnoreProperties(ignoreUnknown = true)`) | 토스가 필드를 늘려도 역직렬화가 깨지지 않습니다 |
| 결과 불명 정리 중 토스 승인 결제의 금액이 우리 주문과 다름 | 확정하지 않고 보류 + error 로그 | 사람이 볼 일입니다 |

#### 4. 그 밖에 고려한 상황

| 상황 | 동작 |
|---|---|
| 같은 결제의 두 승인·두 취소가 동시에 | 행 잠금 + 승인 중 결제·진행 중 취소 1건으로 토스를 한 번만 부릅니다(「동시성 문제」 7·9·10번) |
| 토스를 기다리는 동안 DB 락·커넥션 | T1 → 토스 → T2로 트랜잭션을 나눠 외부 호출 동안 아무것도 쥐지 않습니다(「동시성 문제」 8번) |
| 거절된 시도에 남의 `paymentKey`가 남음 | 승인 요청의 `paymentKey`는 토스가 확인하기 전 값이라 거절되면 지웁니다. 남기면 유니크 제약 때문에 그 키의 진짜 주문이 승인을 시작하지 못합니다 |
| 정리 중 한 건이 예외 | 그 건만 로그를 남기고 나머지를 계속합니다. 실패한 건은 승인 중으로 남아 다음 회차에 다시 묻습니다 |
| 자동 환불이 토스에서 거절됨 | 돈은 빠졌고 좌석은 없는 상태라 자동으로 풀 수 없습니다. `REFUND_REJECTED`로 알리고 error 로그를 남깁니다 |
| 요청 후 14일이 지난 결과 불명 취소 | 토스 멱등키가 15일 유효라, 그 뒤 같은 키는 새 요청이 되어 이중 환불이 될 수 있습니다. 자동 재시도에서 빼고 관리자 대조로 확인합니다 |

### 회원이 받는 결제 오류 응답

| 코드 | 상태 | 뜻 | 회원이 할 일 |
|---|---|---|---|
| `PAYMENT_REJECTED` | 402 | 카드사·토스가 거절. 돈은 빠지지 않음 | 다른 결제수단으로 다시 결제 |
| `PAYMENT_SERVICE_UNAVAILABLE` | 503 | 키 오류·호출 한도·연결 실패로 토스가 판단하지 않음. 돈은 빠지지 않음 | 잠시 후 다시 결제 |
| `PAYMENT_RESULT_UNKNOWN` | 504 | 결과를 모름. 돈이 빠졌을 수 있음 | 다시 결제하지 말고 내역 확인. 서버가 정리함 |
| `PAYMENT_CANCEL_RESULT_UNKNOWN` | 504 | 환불 결과를 모름. 좌석은 그대로 | 잠시 후 내역 확인 또는 재시도. 서버도 같은 키로 다시 보냄 |
| `PAYMENT_IN_PROGRESS` | 409 | 같은 예매의 결제가 아직 승인 중 | 잠시 후 결과 확인 |

### 고려했지만 넣지 않은 것

| 방법 | 넣지 않은 이유 |
|---|---|
| 서킷 브레이커 (Resilience4j) | 토스 장애가 길어질 때 호출을 빨리 끊어 스레드를 아끼는 장치입니다. 지금 트래픽에서는 이득이 작고 의존성·설정이 늘어납니다. 토스 장애 동안 들어온 결제는 503·504로 끝나고 정리 스케줄러가 맞춥니다 |
| 읽기 타임아웃·5xx 자동 재시도 (Spring Retry) | 읽기 타임아웃 뒤 다시 보내면 회원이 최대 2분을 기다립니다. 멱등키가 있어 안전하긴 하지만, 같은 일을 조회 기반 정리가 회원을 붙잡지 않고 합니다 |
| 결과 불명 승인을 승인 API로 다시 보내기 | 같은 멱등키라 이중 결제는 없지만, 좌석을 잃은 예매에 돈을 받은 뒤 다시 환불하게 될 수 있습니다. 조회로 먼저 확인합니다 |
| 웹훅 | 로컬에서 받으려면 공개 URL이 필요하고, 카드 결제는 동기 응답 + 조회로 충분합니다(`docs/toss-payments-guide.md` 5장). 붙인다면 맡을 역할과 설계는 아래 「Webhook」 절에 정리했습니다 |

### 한계

- 스케줄러는 서버 한 대를 가정합니다. 여러 대면 같은 건을 동시에 정리할 수 있습니다. 행 잠금과 상태 재확인으로 한 번만 반영되고 토스 호출도 멱등하지만, 토스 조회가 중복됩니다.
- 결과 불명이 상영 시작 이후까지 이어지면 정리 시점에 예매를 그대로 확정합니다.
- 거절된 결제는 `paymentKey`를 지우므로 관리자 대조가 `orderId`로 묻습니다. 문서 공용 키에서는 이 조회가 동작하지 않습니다.
- 결과 불명·연결 실패는 실제 토스로 일부러 만들 수 없어 테스트로만 확인했습니다. 테스트에서는 스케줄러를 끄고(`scheduling.enabled=false`) 정리 메서드를 직접 부릅니다.

### 테스트

| 클래스 | 확인하는 것 |
|---|---|
| `TossPaymentsClientTest` | `MockRestServiceServer`로 위 분류 표의 응답을 하나씩 흉내 냅니다. 연결 실패 후 같은 멱등키로 한 번만 다시 보내는지, 읽기 타임아웃은 다시 보내지 않는지, 2xx인데 요청과 다른 응답·JSON이 아닌 응답 포함 |
| `ReservationPaymentServiceTest`, `PurchasePaymentServiceTest` | `serviceError` 거절이 실패로 확정되고 503으로 나가는지 |
| `ReservationControllerTest.토스에_닿지_못한_승인은_503이고_결제는_실패로_끝나_새_주문으로_다시_결제할_수_있다` | HTTP 응답과 DB 상태 |
| `PaymentTest` | T1에서 `paymentKey`·승인 시작 시각 저장, 거절 시 `paymentKey` 삭제, 2분 전 정리 거부 |
| `ReservationPaymentRecoveryServiceTest`, `PurchasePaymentRecoveryServiceTest` | 토스 조회 결과별 분기, 한 건이 실패해도 나머지 정리 |
| `ReservationAdminControllerTest` | 확정 / 만료 시각이 지났어도 좌석이 남아 있으면 확정 / 좌석을 잃으면 환불하고 `EXPIRED` 유지 / 자동 환불 결과 불명 → 스케줄러가 같은 멱등키로 마침 / 실패 확정 후 새 주문 / 보류 / 회원 취소의 결과 불명을 스케줄러가 마침 / 2분 전 409 / 회원 403 |
| `PurchaseRecoveryControllerTest` | 확정 시 재고 유지, 실패 확정 시 재고 복구, 조회 불가 시 재고를 묶어 둔 채 보류, 스케줄러가 결과 불명 결제·환불 정리 |

변형 실험으로 테스트가 실제로 잡는지 확인했습니다(되돌림 완료).

| 변형 | 깨지는 테스트 |
|---|---|
| `confirmAfterRecovery`에 만료 검사 추가 (기존 `confirm`처럼) | 만료 시각이 지났어도 좌석이 남아 있으면 확정한다 |
| `completeRefund`가 `EXPIRED` 예매도 `CANCELLED`로 바꿈 | 좌석을 잃으면 `EXPIRED` 유지 (2개) |
| 매점 실패 확정에서 재고 복구 제거 | `expected: 10 but was: 7` |

전체 428 → 483개 통과.

## Webhook

우리 서버가 결제 결과를 제대로 전달받지 못했을 때를 대비하는 방법으로 웹훅을 조사하고, 이 서비스에 붙인다면 어떤 역할을 맡을지 설계했습니다. **구현은 하지 않았습니다.** 출처는 토스페이먼츠 [웹훅 연결하기](https://docs.tosspayments.com/guides/v2/webhook)와 [웹훅 이벤트 레퍼런스](https://docs.tosspayments.com/reference/using-api/webhook-events)입니다.

### Webhook이란

보통의 API 호출은 우리가 요청하고 상대가 응답합니다. 웹훅은 반대로, **상대 서비스에서 어떤 일이 일어났을 때 상대가 우리가 미리 등록해 둔 URL로 HTTP 요청을 보내는 방식**입니다. "역방향 API" 또는 "HTTP 콜백"이라고도 부릅니다.

외부 시스템의 결과를 알아내는 방법은 세 가지이고, 지금 이 서비스는 앞의 두 가지를 씁니다.

| 방법 | 누가 먼저 움직이나 | 언제 알게 되나 | 이 서비스에서 |
|---|---|---|---|
| 동기 응답 | 우리가 요청, 토스가 응답 | 호출이 끝나는 순간 | 정상 승인·취소 (T1 → 토스 → T2) |
| 폴링 (조회 반복) | 우리가 주기적으로 묻는다 | 다음 조회 때 | 결과 불명 정리 스케줄러 (1분 간격, 승인 시작 2분 뒤부터) |
| **웹훅** | 토스가 알린다 | 토스 상태가 바뀐 직후 | 없음 |

웹훅을 받는 쪽에는 공통으로 따라오는 조건이 있습니다.

- **공개 URL이 있어야 합니다.** 상대 서버가 우리에게 접속하므로 `localhost`로는 받을 수 없습니다.
- **같은 알림이 여러 번 올 수 있습니다.** 보내는 쪽은 성공 응답을 못 받으면 다시 보냅니다. 그래서 두 번 받아도 결과가 같게(멱등하게) 처리해야 합니다.
- **순서가 보장되지 않습니다.** 재전송이 끼면 "취소됨"이 "승인됨"보다 먼저 도착할 수 있습니다.
- **누구나 그 URL로 요청을 보낼 수 있습니다.** 진짜 상대가 보낸 것인지 확인할 수단(서명, 비밀값, 재조회)이 필요합니다.

### Toss Payments의 Webhook

**등록과 형식**

- 개발자센터 **웹훅** 메뉴에서 이름, URL, 받을 이벤트를 등록합니다. 상점아이디(MID)마다 따로 설정됩니다.
- 온라인에서 접근할 수 있는 주소여야 합니다. 로컬에서 시험하려면 `ngrok http 8080`처럼 터널을 열고 그 주소를 등록합니다. HTTP도 받지만 HTTPS를 권장합니다.
- 이벤트는 **HTTP POST + JSON**입니다. 결제 상태 변경 이벤트의 본문은 `{ eventType, createdAt, data }`이고, `data`는 승인·조회 API가 돌려주는 것과 같은 Payment 객체입니다.
- 헤더로 `tosspayments-webhook-transmission-time`(발송 시각), `tosspayments-webhook-transmission-retried-count`(재전송 횟수), `tosspayments-webhook-transmission-id`(전송 식별자)가 오고, 일부 이벤트에만 `tosspayments-webhook-signature`가 붙습니다.
- 개발자센터에서 전송 기록(전송 중 · 성공 · 실패)을 볼 수 있고, 최종 실패하면 이메일로 알려 줍니다.

**응답 규칙과 재전송**

웹훅을 받았으면 **10초 안에 200**을 돌려줘야 합니다. 그러지 못하면 간격을 4배씩 늘리며 **최대 7번** 다시 보냅니다.

| 재전송 | 1 | 2 | 3 | 4 | 5 | 6 | 7 |
|---|---|---|---|---|---|---|---|
| 직전 전송과의 간격 | 1분 | 4분 | 16분 | 64분 | 256분 | 1024분 | 4096분 |
| 최초 전송부터 | 1분 | 5분 | 21분 | 1시간 25분 | 5시간 41분 | 22시간 45분 | **3일 19시간** |

서버가 몇 시간 내려가 있어도 그 사이 바뀐 결제 상태를 재기동 뒤 받을 수 있다는 점이 웹훅의 큰 장점입니다. 전송 중일 때 개발자센터에서 **다시 시도**를 누르면 남은 재전송은 무효가 되고 새 1회 요청이 갑니다.

**진짜 토스가 보낸 것인지 확인하는 방법**

| 이벤트 | 검증 수단 |
|---|---|
| `PAYMENT_STATUS_CHANGED` | **없음.** 조회 API로 다시 확인해야 합니다 |
| `DEPOSIT_CALLBACK` | 본문 `secret`이 승인 API 응답의 `secret`과 같은지 비교 |
| `payout.changed`, `seller.changed` | `tosspayments-webhook-signature`: `{본문}:{transmission-time}`을 보안 키로 HMAC-SHA256 한 값과 비교 |

이 서비스가 쓸 `PAYMENT_STATUS_CHANGED`에는 서명도 비밀값도 없습니다. 이 사실이 아래 설계의 출발점입니다.

**이벤트 종류**

| 이벤트 | 무엇 | 이 서비스 |
|---|---|---|
| `PAYMENT_STATUS_CHANGED` | 결제 상태 변경. 모든 결제수단. 승인(`DONE`), 취소(`CANCELED`·`PARTIAL_CANCELED`), 실패(`ABORTED`), 만료(`EXPIRED`) | **사용** |
| `DEPOSIT_CALLBACK` | 가상계좌 입금·입금 취소 | 가상계좌를 받지 않아 지금은 쓰지 않음 |
| `CANCEL_STATUS_CHANGED` | 해외 간편결제 취소 상태 | 쓰지 않음. 국내 결제의 취소는 `PAYMENT_STATUS_CHANGED`로 옴 |
| `METHOD_UPDATED`, `CUSTOMER_STATUS_CHANGED`, `BILLING_DELETED`, `payout.changed`, `seller.changed`, `ORDER_PAYMENT_STATUS_CHANGED`, `ars-reservation.changed` | 브랜드페이, 자동결제, 지급대행, 링크페이, ARS | 쓰지 않음 |

결제창에서 30분 안에 인증하지 않거나, 인증 후 10분 안에 승인 API를 부르지 않으면 토스가 결제를 `EXPIRED`로 바꿉니다. 이것도 `PAYMENT_STATUS_CHANGED`로 옵니다.

### 결제 시스템에서 Webhook을 쓰는 이유

결제는 우리 서버와 결제사 서버가 같은 돈의 상태를 각자 기록합니다. 동기 응답만으로는 두 기록이 어긋나는 순간을 다 메울 수 없습니다.

1. **결과가 나중에 정해지는 결제수단.** 가상계좌는 구매자가 은행에서 입금할 때 결제가 끝납니다. 승인 API를 부른 시점에는 결과가 없으므로 결제사가 알려 주는 수밖에 없습니다.
2. **응답이 사라지는 경우.** 읽기 타임아웃, 응답 직전의 서버 다운, 네트워크 단절. 결제사에서는 돈이 빠졌는데 우리는 모릅니다. 웹훅은 결제사가 성공 응답을 받을 때까지 며칠에 걸쳐 다시 보내 줍니다.
3. **우리 코드 밖에서 일어난 변경.** 상점관리자 화면에서 직접 한 취소, 결제창을 방치해 생긴 만료처럼 우리가 API를 부르지 않은 변경에는 동기 응답이 없습니다.
4. **폴링보다 빠르고 쌉니다.** 결과 불명은 드물어서 주기적 조회는 대부분 헛걸음입니다. 웹훅은 바뀐 것만, 바뀐 직후에 옵니다.

웹훅도 유실될 수 있습니다(URL 미등록, 방화벽, 7번 모두 실패). 그래서 **웹훅으로 빨리 알고, 폴링을 안전망으로 남겨 두는** 조합이 일반적입니다. 웹훅은 폴링을 대체하기보다 정리 시점을 앞당깁니다.

### 이 서비스에서 Webhook이 맡을 역할

**결제 완료와 예매 확정은 정상 경로에서는 웹훅이 맡지 않습니다.** 승인 API가 `DONE`을 돌려주면 같은 요청의 T2가 결제 `DONE`과 예매 `RESERVED`를 함께 확정합니다. 그 뒤에 도착하는 웹훅은 "이미 반영됨"으로 넘깁니다. 웹훅이 결제 완료·예매 확정을 맡는 것은 **그 T2가 일어나지 못했을 때**입니다(아래 1~3번).

| # | 상황 | 지금 | 웹훅이 오면 |
|---|---|---|---|
| 1 | 예매 승인 결과 불명 (읽기 타임아웃·5xx·`PROVIDER_ERROR`) | 결제가 `IN_PROGRESS`로 남고 회원은 504. 승인 시작 2분 뒤부터 스케줄러가 조회 | `DONE`이면 예매 확정, 좌석을 잃었으면 승인 확정 후 자동 환불. `ABORTED`·`EXPIRED`면 결제 실패, 좌석은 선점 시간 안에서 유지 |
| 2 | 토스 승인 후 T2 전에 서버 다운·DB 오류 | 재기동 뒤 스케줄러가 정리 | 내려가 있는 동안 토스가 재전송하고, 재기동 뒤 받은 웹훅으로 정리 |
| 3 | 매점 승인 결과 불명 | 2분 넘게 재고가 묶여 있다가 스케줄러가 정리 | `DONE`이면 구매 확정, 실패면 재고 복구를 바로 함 |
| 4 | 환불 결과 불명 (회원 취소·매장 환불·좌석을 잃은 자동 환불) | `PaymentCancel`이 `IN_PROGRESS`. 2분 뒤 같은 멱등키로 다시 보냄 | 취소 웹훅이 오면 같은 멱등키로 바로 재시도. 토스가 첫 응답을 돌려줘 좌석 해제·누적 취소액이 반영됨 |
| 5 | 토스 상점관리자에서 직접 취소 | **감지 못함.** 스케줄러는 `IN_PROGRESS`만 보고, 회원 조회는 우리 DB만 읽음 | 우리 기록과 토스 상태의 불일치를 남기고 error 로그. 관리자가 확인 |
| 6 | 실패로 확정한 결제가 토스에서는 `DONE` (정상이면 없는 일) | **감지 못함.** `fail()`이 `paymentKey`를 지워 조회할 키도 없음 | 웹훅 본문의 `paymentKey`로 조회해 주문번호가 같으면 error 로그. 관리자 환불 대상 |
| 7 | 결제창 이탈 (인증하지 않았거나 승인을 부르지 않음) | 결제가 `READY`로 남음. 예매는 선점이 만료되고 매점은 재고를 차감하지 않아 피해 없음 | 기록만 함 |
| 8 | 정상 승인·정상 취소 | 동기 응답으로 확정 | 이미 반영됨. 토스 조회 없이 끝 |
| 9 | 가상계좌를 도입한다면 | 받지 않음 | `DEPOSIT_CALLBACK`이 필수. 선점 10분과 입금 기한의 충돌은 따로 설계해야 함 |

정리하면 웹훅의 역할은 **"토스 쪽 상태가 바뀌었다"는 신호를 받아, 이미 있는 결과 불명 정리 로직을 그 자리에서 실행시키는 것**입니다. 판단은 결제 세션 5에서 만든 정리 서비스가 그대로 합니다. 스케줄러가 볼 수 없던 5·6번은 웹훅으로만 새로 잡힙니다.

```
정리 계기                                 판단 (기존 코드)

스케줄러    1분마다, 2분 지난 IN_PROGRESS ─┐
관리자 API  orderId 지정, 즉시          ─┼─▶ 토스 조회 ──┬─ cgv-r- ─▶ 예매 결제 정리 (행 잠금 · 상태 재확인)
웹훅 (신규) 토스 상태가 바뀐 직후         ─┘   paymentKey  └─ cgv-p- ─▶ 매점 결제 정리 (행 잠금 · 상태 재확인)
```

세 계기가 같은 판단과 같은 행 잠금 + 상태 재확인을 쓰므로, 같은 결제를 셋이 동시에 건드려도 한 번만 반영됩니다.

### 구현한다면

#### 원칙

| 원칙 | 이유 |
|---|---|
| **본문은 단서로만 쓴다.** `orderId`·`paymentKey`만 꺼내고, 상태는 `paymentKey`로 토스를 다시 조회한 결과로 판단 | 서명이 없어 누구나 같은 본문을 보낼 수 있습니다. 조회는 우리 시크릿 키로 하므로 위조할 수 없습니다. 몇 번째로 도착한 웹훅이든 항상 **지금** 토스 상태로 맞추므로 중복과 순서 뒤바뀜도 함께 해결됩니다 |
| **받기와 처리를 나눈다.** 저장하고 200, 처리는 커밋 후 비동기 | 토스 조회는 읽기 타임아웃이 60초라 10초 응답 규칙을 지킬 수 없습니다 |
| **판단은 하나만 둔다** | 웹훅 전용 판단을 따로 만들면 스케줄러와 결과가 어긋날 수 있습니다. 정리 서비스의 토스 상태별 분기를 함께 씁니다 |
| **스케줄러는 남긴다** | 웹훅은 유실될 수 있고, 로컬 개발 중에는 공개 URL이 없어 오지 않습니다 |

#### 처리 흐름

```
토스                 웹훅 컨트롤러           DB              웹훅 처리기            예매·매점 정리
 │                       │                  │                    │                      │
 │ ── POST 웹훅 ───────▶ │                  │                    │                      │
 │                       │ ── 이벤트 저장 ─▶ │                    │                      │
 │ ◀────────── 200 ───── │  (여기까지 10초 안)                     │                      │
 │                       │                  │                    │                      │
 │                       │ ── 커밋 후 비동기 이벤트 ─────────────▶ │                      │
 │                       │                  │ ◀── 우리 결제 상태 ─ │  이미 같으면 끝         │
 │ ◀──────────── GET /v1/payments/{paymentKey} ──────────────── │                      │
 │ ───────────── 현재 Payment (판단 근거) ─────────────────────▶ │                      │
 │                       │                  │                    │ ── 토스 상태 전달 ──▶ │
 │                       │                  │ ◀──────────── 행 잠금 · 상태 재확인 · 반영 ─ │
 │                       │                  │ ◀── 처리 결과 기록 ─ │                      │
```

- 서비스는 같은 도메인 것만 쓴다는 규칙 때문에 결제 도메인의 처리기가 예매·매점 서비스를 직접 부르지 않습니다. 처리기는 조회 결과를 스프링 이벤트로 발행하고, 예매·매점 도메인의 리스너가 주문번호 접두사(`cgv-r-` 예매, `cgv-p-` 매점)를 보고 자기 정리 서비스를 부릅니다. 토스 조회는 처리기에서 한 번만 합니다.
- 200을 준 뒤 처리 전에 서버가 죽으면 토스는 다시 보내지 않습니다. 처리 결과가 비어 있는 이벤트 행을 기존 스케줄러가 집어 다시 처리합니다.

#### 웹훅은 2분을 기다리지 않는다

지금 정리 서비스는 승인 시작 후 2분(`Payment.RECOVERY_DELAY`)이 지나야 동작합니다. 원래 요청이 아직 토스 응답을 기다리고 있을 수 있어서입니다. 그런데 토스는 상태가 바뀐 직후 웹훅을 보내므로 웹훅은 거의 항상 이 2분 안에 도착합니다. 규칙을 그대로 두면 웹훅은 스케줄러보다 빠르지 않습니다.

```
읽기 타임아웃 상황 (토스는 승인했는데 응답이 끊김)

토스        ● 5초: 승인 완료, 웹훅 발송
원래 요청   ━━━━━━━━━━━━━━━━━━━━┫ 60초: 읽기 타임아웃 → 회원에게 504
웹훅 즉시   ● 6초: 예매 확정                                          ← 채택
2분 유지    ○ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ● 2분: 예매 확정 (스케줄러와 같은 시점)
            ├──────────┼──────────┼──────────┼──────────┤
            0초       30초        1분       1분 30초      2분
```

그래서 **웹훅은 2분을 기다리지 않고 바로 처리합니다.** 웹훅 처리와 원래 요청의 T2가 겹쳐도 둘 다 예매(구매) 행을 먼저 잠그고 결제가 아직 `IN_PROGRESS`인지 다시 확인하므로 한 번만 반영됩니다. 지금 스케줄러가 쓰는 장치와 같습니다. 대신 원래 요청 쪽을 고쳐야 합니다.

| 바꿀 것 | 이유 |
|---|---|
| 원래 요청의 T2가 **이미 같은 결과로 반영된 결제**를 만나면 성공으로 응답 | 지금은 `Payment.approve()`가 `IN_PROGRESS`가 아니면 `INVALID_PAYMENT_STATUS`를 던지고, 오케스트레이터가 이를 잡아 504로 바꿉니다. 웹훅이 먼저 확정하면 **성공한 결제가 회원에게 504로** 나갑니다 |
| `Payment.approve()`·`fail()`에 "이미 같은 결과면 그대로 둔다" 분기 추가 | 위를 위한 엔티티 변경입니다. `approve()`는 같은 `paymentKey`로 이미 `DONE`일 때만 넘어가고, 다른 상태면 지금처럼 예외입니다 |
| 거절 보상(매점 재고 복구)은 지금처럼 `IN_PROGRESS → FAILED` 전이에 성공한 쪽만 | 웹훅의 실패 확정과 원래 요청의 거절 처리가 겹쳐도 재고를 두 번 되돌리지 않습니다 |
| (선택) 읽기 타임아웃으로 504를 주기 직전에 우리 결제를 다시 읽어 `DONE`이면 성공 응답 | 그 사이 웹훅이 확정했다면 회원이 504를 보지 않습니다 |

#### 판단 표

"토스 상태"는 웹훅 본문이 아니라 조회 결과입니다. `IN_PROGRESS` 행은 지금 정리 서비스의 분기와 같고, 나머지 행이 웹훅 때문에 새로 생깁니다.

| 우리 결제 | 토스 조회 결과 | 동작 |
|---|---|---|
| `READY` | `EXPIRED` · `ABORTED` | 기록만 (결제창 이탈) |
| `READY` | `DONE` | error 로그 + 관리자 확인. T1이 승인 전에 `IN_PROGRESS`로 바꾸므로 정상이면 없는 조합 |
| `IN_PROGRESS` | `DONE`, 주문번호·금액 일치 | 승인 반영. 예매가 `PENDING`이면 확정, 좌석을 잃었으면 승인 확정 + 전액 환불. 매점은 구매 확정 |
| `IN_PROGRESS` | `DONE`, 주문번호·금액 다름 | 보류 + error 로그 |
| `IN_PROGRESS` | `ABORTED` · `EXPIRED` | 실패 확정. 예매는 좌석 유지, 매점은 재고 복구 |
| `IN_PROGRESS` | `READY` · `IN_PROGRESS` · 취소 상태 · 조회 불가 | 보류. 다음 웹훅이나 스케줄러가 다시 봄 |
| `DONE` · `PARTIAL_CANCELED` | 취소 상태 + 우리에게 진행 중 취소 있음 | 그 `PaymentCancel`을 저장된 멱등키로 바로 재시도 |
| `DONE` · `PARTIAL_CANCELED` | 취소 상태 + 진행 중 취소 없음, 누적 취소액이 다름 | 외부 취소. 불일치 기록 + error 로그 → 관리자 |
| `DONE` · `PARTIAL_CANCELED` | 누적 취소액이 같음 | 끝 (정상 경로의 웹훅) |
| `FAILED` | `DONE`, 주문번호 일치 | error 로그 + 관리자 환불 대상. 웹훅 본문의 `paymentKey`가 유일한 단서 |
| `FAILED` · `CANCELED` | 그 외 | 끝 |

우리 결제가 `READY`나 `FAILED`이면 저장된 `paymentKey`가 없으므로 본문의 키로 조회하고, 조회 응답의 주문번호가 우리 주문과 같을 때만 표를 적용합니다.

#### 엔드포인트와 응답 코드

- `POST /api/payments/webhook/toss`. 토스는 Bearer 토큰을 보낼 수 없으므로 `SecurityConfig`에서 `permitAll`로 엽니다.
- 주문번호가 `cgv-r-`·`cgv-p-`로 시작하고 우리 DB에 있는 주문만 처리합니다.
- 위조된 요청이 할 수 있는 일은 토스 조회 한 번을 일으키는 것뿐입니다. 상태는 조회 결과로만 바뀝니다. 새 비밀값도 생기지 않습니다.

토스는 200이 아닌 응답을 받을 때만 다시 보냅니다. 그래서 **다시 받아야 의미가 있을 때만** 200이 아닌 응답을 줍니다.

| 경우 | 응답 | 이유 |
|---|---|---|
| 저장 성공 | 200 | "받았다"는 뜻. 처리 결과와 상관없음 |
| 이미 받은 `transmission-id` | 200 | 재전송을 멈춤 |
| 모르는 `eventType` · 우리 주문 아님 · 본문 해석 불가 | 200 + 로그 | 다시 받아도 결과가 같음 |
| DB 저장 실패 | 500 | 받은 사실을 남기지 못함. 토스의 재전송에 기대는 유일한 경우 |

#### 저장할 것 — `WebhookEvent`

| 필드 | 내용 |
|---|---|
| `transmissionId` | 헤더 값, 유니크. 중복 저장을 막음. 같은 이벤트가 다른 id로 와도 처리가 멱등하므로 정확성은 여기에 기대지 않음 |
| `eventType`, `orderId`, `paymentKey` | 본문에서 꺼낸 단서 |
| `reportedStatus` | 본문이 말한 상태. 판단에는 쓰지 않고 기록용 |
| `retriedCount` | 헤더의 재전송 횟수. 우리 쪽 장애를 되짚을 때 씀 |
| `payload` | 원문 JSON |
| `receivedAt`, `processedAt` | `processedAt`이 비어 있고 받은 지 1분이 지난 행을 스케줄러가 다시 처리 |
| `result` | `APPLIED` · `ALREADY_APPLIED` · `UNRESOLVED` · `MISMATCH` · `IGNORED` |

#### 만들고 고칠 파일

| | 파일 | 할 일 |
|---|---|---|
| 신설 | `payment/controller/TossWebhookController` | 헤더·본문 받기 → 저장 → 200 |
| 신설 | `payment/dto/TossWebhookRequest` | `record(eventType, createdAt, data)`. `data`는 기존 `TossPayment` 재사용 |
| 신설 | `payment/entity/WebhookEvent`, `WebhookEventResult`, `payment/repository/WebhookEventRepository` | 위 표 |
| 신설 | `payment/service/PaymentWebhookService` | 저장, 커밋 후 처리(우리 상태 확인 → 토스 조회 → 이벤트 발행), 미처리 행 재처리 |
| 신설 | `payment/dto/TossPaymentChangedEvent` | 도메인 사이로 넘기는 스프링 이벤트 (`orderId`, 조회한 `TossPayment`) |
| 신설 | `reservation/service/ReservationPaymentWebhookListener`, `store/service/PurchasePaymentWebhookListener` | 접두사로 자기 주문만 골라 정리 서비스 호출 |
| 신설 | `global/config/AsyncConfig` | `@EnableAsync` + 웹훅 전용 스레드 풀 |
| 수정 | `ReservationPaymentRecoveryService`, `PurchasePaymentRecoveryService` | 토스 상태별 분기를 "조회한 결과를 받아 반영"하는 메서드로 떼어 웹훅과 스케줄러가 함께 씀. 판단 표의 `READY`·`DONE`·`FAILED` 행 추가 |
| 수정 | `ReservationService`, `PurchaseService`, `Payment` | 원래 요청의 T2가 이미 반영된 결제를 성공으로 처리 |
| 수정 | `SecurityConfig` | 웹훅 경로 `permitAll` |

#### 테스트 계획

| 대상 | 확인할 것 |
|---|---|
| 컨트롤러 | 인증 없이 200 · 행 저장 · 같은 `transmission-id` 두 번이면 한 행 · 저장 실패면 500 |
| 처리 서비스 | 판단 표의 칸마다 한 테스트. **본문은 `DONE`인데 조회는 `ABORTED`면 실패로 확정**(본문을 믿지 않음) · 같은 이벤트 두 번이면 한 번 반영 · 취소 웹훅이 승인 웹훅보다 먼저 와도 최종 상태가 같음 |
| 경합 | 웹훅이 먼저 확정한 뒤 원래 T2가 와도 회원은 200 (`RaceGate`로 순서 고정) · 웹훅의 실패 확정과 원래 요청의 거절 처리가 겹쳐도 재고는 한 번만 복구 |
| 재처리 | 저장 후 처리 전에 멈춘 이벤트를 스케줄러 메서드가 처리 |
| 실제 연결 | `ngrok http 8080` → 개발자센터에 URL 등록 → 테스트 결제·취소 → 전송 기록과 이벤트 행 확인 |

지금 쓰는 문서 공용 키(`test_gck_docs_` / `test_gsk_docs_`)로 한 결제는 토스 문서용 상점의 결제입니다. 웹훅은 결제가 일어난 상점으로만 가므로, 내 상점에 웹훅을 등록해도 우리 테스트 결제의 웹훅은 오지 않습니다. 개발자센터에서 내 상점 키로 나오는 것은 API 개별 연동 키(`test_ck_` / `test_sk_`)와 보안 키뿐이고, 이 키는 결제위젯이 아니라 결제창 연동용이라 지금 구조에 쓸 수 없습니다(섞으면 `INVALID_API_KEY`). 결제위젯 연동 키는 전자결제 신청 전에는 문서 공용 키만 보입니다. 그래서 실제 웹훅 수신은 전자결제를 신청하거나 결제창 연동으로 바꿔야 확인할 수 있고, 그 전까지는 테스트에서 요청을 직접 만들어 확인합니다.

### 고려했지만 넣지 않을 것

| 방법 | 넣지 않는 이유 |
|---|---|
| 웹훅 본문만 보고 상태 바꾸기 | `PAYMENT_STATUS_CHANGED`에는 서명이 없어 누구나 같은 본문을 보낼 수 있습니다 |
| 웹훅을 받고 승인 API를 다시 부르기 | 「외부 API 통신과 예외 처리」에서와 같은 이유입니다. 좌석을 잃은 예매에 돈을 받고 다시 환불하게 될 수 있어 조회로 확인합니다 |
| 외부 취소를 자동으로 반영하기 | 상점관리자에서 부분 취소한 금액이 어느 좌석·어느 상품인지 알 수 없습니다. 사람이 판단할 일입니다 |
| 요청 안에서 동기로 처리하기 | 토스 조회가 최대 60초라 10초 응답 규칙을 지킬 수 없습니다 |
| 메시지 큐 (Kafka 등) | 지금 규모에서는 테이블 + 커밋 후 비동기 + 스케줄러 재처리로 충분합니다 |

### 한계

- 서버가 여러 대면 같은 이벤트를 동시에 처리할 수 있습니다. 행 잠금과 상태 재확인으로 한 번만 반영되지만 토스 조회는 중복됩니다. 스케줄러와 같은 한계입니다.
- 로컬 개발 중에는 웹훅이 오지 않습니다. 이 기간에는 스케줄러만으로 정리합니다.
- 웹훅 처리 스레드 풀이 가득 차면 처리가 밀립니다. 저장은 이미 끝났으므로 유실되지 않고 스케줄러가 이어서 처리합니다.
