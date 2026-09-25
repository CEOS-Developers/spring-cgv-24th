# 리팩토링 로그

CGV 실서비스 흐름에 맞춰 세션 단위로 리팩토링한 기록.

---

## 세션 0: 현황

코드 변경 없음. 현재 상태를 정리하고 이후 세션의 영향 범위를 확정하기 위한 세션.

### 1. 엔티티와 관계

엔티티 13개, enum 4개. 모든 엔티티는 `BaseTimeEntity`(createdAt/updatedAt)를 상속한다.

```
users ─┬─< reservation ──< reservation_seat >── screening
       ├─< purchase ──< purchase_product >── product
       ├─< movie_like >── movie
       └─< branch_like >── branch

branch ─┬─< theater ──< screening >── movie
        ├─< stock >── product
        ├─< purchase
        └─< branch_like

screening ──< reservation_seat     (좌석 중복 방지용 직접 FK)
```

| 부모 | 자식 | 방향 | 비고 |
|---|---|---|---|
| users | reservation, purchase, movie_like, branch_like | 단방향 | 모두 LAZY |
| branch | theater, stock, purchase, branch_like | 단방향 | |
| theater | screening | 단방향 | 좌석 배치는 `theater_type` enum이 보유 |
| movie | screening, movie_like | 단방향 | |
| screening | reservation | 단방향 | |
| screening | reservation_seat | 단방향 | 좌석 중복 방지를 위한 직접 FK |
| reservation | reservation_seat | **양방향** | cascade ALL + orphanRemoval (헤더-디테일) |
| purchase | purchase_product | **양방향** | cascade ALL + orphanRemoval (헤더-디테일) |

enum:

| enum | 값 | 특징 |
|---|---|---|
| `Region` | SEOUL … JEJU (10개) | 지역 탭. 선언 순서가 노출 순서 |
| `BranchStatus` | OPEN / TEMPORARILY_CLOSED / CLOSED | `isReservable()` 보유 |
| `TheaterType` | STANDARD(8×10) / SPECIAL(10×20) | `getTotalSeatCount()`, `isValidSeat()` 보유 · 세션 1-1에서 정정 |
| `ReservationStatus` | RESERVED / CANCELLED | 메서드 없음 |

유니크 제약:

- `uk_seat_screening_row_col` — (screening_id, row_num, col_num) **좌석 중복 예매 방지의 핵심**
- `uk_stock_branch_product` — (branch_id, product_id)
- `uk_branch_like_user_branch` / `uk_movie_like_user_movie` — 찜 중복 방지
- `users.login_id`

좌석 테이블은 없다. 배치는 `TheaterType.rowCount/colCount`, 좌석 위치는
`ReservationSeat.rowNum/colNum`으로 표현한다. DB가 좌석 범위를 검증할 수 없으므로
`TheaterType.isValidSeat()`가 도메인에서 책임진다.

### 2. API 엔드포인트

| 메서드 | 경로 | 하는 일 |
|---|---|---|
| GET | `/api/branches` | 지점 목록. `region` 탭 또는 `keyword` 검색. `CLOSED` 제외, 특별관 라벨 집계 |
| GET | `/api/branches/{id}` | 지점 단건 + 소속 상영관. 상태로 거르지 않음(폐관도 조회됨) |
| GET | `/api/movies` | 영화 목록 |
| GET | `/api/movies/{id}` | 영화 단건 |
| GET | `/api/screenings` | 회차 목록. `movieId`/`branchId`/`date` 옵션 필터, 잔여좌석 포함 |
| GET | `/api/screenings/{id}/seats` | 상영관 크기(rowCount/colCount) + 예매된 좌석 라벨 |
| POST | `/api/reservations` | 예매 생성 (201) |
| GET | `/api/reservations/{id}` | 예매 단건 |
| DELETE | `/api/reservations/{id}` | 예매 취소 |

모든 응답은 `ApiResponse<T>`로 감싼다.

**미구현**

- 사용자 API — Spring Security 복습 후 3주차 과제하며 구현 예정. 현재는 Request DTO로 `userId`를 직접 받는다
- 찜 API — `BranchLike`/`MovieLike` 엔티티·리포지토리만 존재
- 매점 API — `store` 도메인에 entity + repository만 있고 controller/service/dto가 없다

### 3. 예매 상태 전이 (현재)

```
   (생성)
      │  Reservation 생성자에서 RESERVED 고정
      ▼
  RESERVED ──── cancel() ────▶ CANCELLED
                                   │
                                   └── 재호출 시 ALREADY_CANCELLED (409)
```

- 중간 상태가 없다. 생성 즉시 확정 예매다. 결제 개념이 없다.
- `cancel()`은 `cancelledAt`을 기록하고 `seats.clear()`로 좌석 행을 삭제한다.
  `uk_seat_screening_row_col` 때문에 행이 남으면 해당 좌석을 재판매할 수 없어서다.
- **부작용**: 좌석 행이 사라지므로 취소된 예매는 `getTotalPrice()`가 0이 되고
  `ReservationResponse.seats`가 빈 배열이 된다. 어느 좌석을 취소했는지 이력이 남지 않는다.
  → 세션 2 재검토 대상.

동시성 처리는 `ReservationService.create()`의 2단 방어로 되어 있다.

1. pre-check — `reservationSeatRepository.findByScreeningId()`로 이미 잡힌 좌석 조회 후 비교
2. 안전망 — `saveAndFlush()`의 `DataIntegrityViolationException`을 잡아
   `SEAT_ALREADY_RESERVED`로 변환. 실제 방어선은 DB 유니크 제약이다.

### 4. 패키지 구조와 계층

```
com.ceos24.cgv
├── domain
│   ├── branch      controller service repository dto entity
│   ├── movie       controller service repository dto entity
│   ├── screening   controller service repository dto entity
│   ├── reservation controller service repository dto entity
│   ├── store       repository entity          ← controller/service/dto 없음
│   └── user        repository entity          ← controller/service/dto 없음
└── global
    ├── config      JpaConfig, SwaggerConfig
    ├── entity      BaseTimeEntity
    ├── exception   CustomException, ErrorCode, GlobalExceptionHandler
    └── response    ApiResponse
```

계층별 현재 방식:

- **Controller** — 서비스 호출과 `ApiResponse` 래핑만. Swagger 어노테이션 부착
- **Service** — `@RequiredArgsConstructor`, 클래스에 `@Transactional(readOnly = true)`,
  쓰기 메서드에만 `@Transactional`
- **Repository** — Spring Data JPA. N+1 회피용 `JOIN FETCH` 쿼리와 인터페이스 프로젝션
  (`BranchTheaterType`, `SeatCountProjection`, `SeatPositionProjection`)
- **DTO** — `record` + 정적 팩토리 `from()`. 중첩 record로 요약 표현
  (`ScreeningSummary`, `TheaterSummary`, `MovieSummary`)
- **예외** — `CustomException` + `ErrorCode` enum. `global/exception` 한 곳

도메인 간 참조 규칙(entity/repository는 허용, service는 같은 도메인만)은 지켜지고 있다.

- `ScreeningService` → `ReservationSeatRepository`
- `ReservationService` → `ScreeningRepository`, `UserRepository`

테스트는 Controller 통합 테스트(`ControllerIntegrationTest` 상속)와 Service 테스트로
나뉘고, 픽스처는 `TestFixtures`에 모여 있다. 현재 branch 11개 / movie 3개 /
screening 12개 / reservation 26개.

### 5. 세션별 영향 범위

#### (1) 극장·상영관 스키마 — **완료**

`a5bbd24`, `84cc465`, `a4eba8d`에서 이미 반영되었다.

- `Branch`에 `region`, `status`, `description`, `imageUrl` 추가
- `Region`, `BranchStatus` enum 신설
- `TheaterType`을 테이블이 아닌 enum으로 확정(rowCount/colCount 보유)
- 지역 탭 필터 / 지역명·지점명 검색 API, 특별관 라벨 집계

좌석은 Seat 엔티티 없이 `TheaterType` + `ReservationSeat.rowNum/colNum`으로 표현하는
구조가 확정이므로 추가 작업 없음.

`TheaterType`의 값 구성은 세션 1-1에서 정정한다.

#### (2) ReservationStatus에 PENDING 추가 + 예매 흐름 재설계

영향받는 파일:

| 파일 | 내용 |
|---|---|
| `ReservationStatus` | PENDING 및 확정/만료 상태 추가 |
| `Reservation` | 생성 시 초기 상태, `cancel()`, 확정·만료 전이 메서드 |
| `ReservationService.create()` | 7단계 전부 재검토 + 확정/만료 경로 신설 |
| `ReservationSeatRepository` | `countGroupedByScreeningIds()`, `findPositionsByScreeningId()`에 **상태 조건이 없다.** `reservation` 조인 필요 |
| `ScreeningService` | `search()` 잔여좌석, `getSeats()` 좌석 라벨 — 세션 3의 전제 |
| `ReservationController` | 결제 확정 엔드포인트 추가 |
| `ReservationResponse` | 상태·만료시각 표현 |
| `ErrorCode` | 전이 실패 코드 추가 |
| 테스트 | `ReservationServiceTest`, `ReservationControllerTest`, `ScreeningServiceTest` 일부 |

세션 2에서 결정할 쟁점:

- **PENDING 좌석도 점유로 본다.** 실제 CGV도 결제 전 선택 단계에서 남이 그 좌석을 잡지 못한다.
  따라서 예매 가능 좌석에서 제외해야 한다.
- 그러려면 PENDING도 `reservation_seat` 행을 실제로 만들어야 한다. 그래야
  `uk_seat_screening_row_col`이 그대로 선점 잠금 역할을 한다. 별도 비관적 락은 불필요하고,
  기존 `DataIntegrityViolationException` 안전망이 계속 유효하다.
- **만료 처리가 새 숙제다.** 결제를 포기한 PENDING 행이 유니크 인덱스를 계속 점유하면
  그 좌석이 영원히 잠긴다. 선점 만료 시각을 어디에 둘지, 만료된 행을 언제 걷어낼지
  (조회·생성 시점 lazy 정리 vs 스케줄러)를 정해야 한다.
- 취소 시 `seats.clear()` 재검토. 이력 보존과 재판매 가능성을 동시에 만족시켜야 한다.

#### (3) 회차 조회 API 재설계

영향받는 파일: `ScreeningController`, `ScreeningService`, `ScreeningRepository`,
`ScreeningResponse`, `ScreeningSeatsResponse`, 관련 테스트.

현재는 단일 평면 리스트 + 옵션 필터 3개(`movieId`/`branchId`/`date`)라
"영화 → 지점 → 날짜 → 시간" 실사용 흐름과 맞지 않는다. 잔여좌석 집계 로직이
세션 2의 상태 모델에 직접 종속된다.

#### (4) 찜 기능

`BranchLike`/`MovieLike` 엔티티와 리포지토리(`existsByUserIdAnd...`)는 이미 있다.
각 도메인에 controller/service/dto를 신규 생성한다. `userId`는 현행대로 요청에서 받는다.
`ErrorCode` 추가 필요. 목록 응답(`BranchResponse`, `MovieResponse`)에 찜 여부를 넣을지 결정해야 한다.

#### (5) 매점 구매

`store` 도메인에 controller/service/dto가 전무하다. 반면 도메인 로직은 준비되어 있다.

- `Stock.decrease()` — 재고 부족 시 `OUT_OF_STOCK`
- `Purchase.addItem()` — 자식 추가와 총액 갱신을 한 메서드에서 처리
- `ErrorCode.OUT_OF_STOCK`, `PRODUCT_NOT_FOUND` 이미 존재

재고 차감 동시성 처리가 쟁점. 좌석과 달리 유니크 제약으로는 막을 수 없다.

### 6. 순서 검토 결론

**현행 유지: (1) 완료 → (2) → (3) → (4) → (5)**

- **(2)가 (3)보다 먼저여야 한다.** PENDING이 "예매 가능 좌석"의 판정 기준을 바꾸므로,
  (3)을 먼저 하면 잔여좌석 집계와 좌석 조회 쿼리를 두 번 고치게 된다.
  실사용 흐름은 회차 조회가 앞서지만, 의존 방향은 반대다.
- (4)·(5)는 다른 세션에 의존하지 않아 언제든 가능하다. 예매 본류를 끝낸 뒤에 두는 것이 맞다.
- (4)를 (5)보다 먼저 두는 것도 타당하다. 찜이 더 작고, 목록 응답에 찜 여부를 얹을지
  결정하면서 조회 응답 구조를 한 번 더 점검하게 된다.

---

## 세션 1-1: 상영관 종류 2단 구조

세션 1의 보완. 세션 2 착수 전에 상영관 스키마를 확정하기 위해 처리했다.

### 문제

상영관 종류는 **대분류(일반관/특별관) → 실제 종류(IMAX·4DX·SCREENX)** 2단 구조이고
좌석 배치는 실제 종류가 결정한다. 그런데 `TheaterType`은 `STANDARD`/`SPECIAL` 두 값뿐이라
대분류가 곧 종류가 되어 있었다. "특별관"이라는 한 덩어리가 단일 배치(10×20)를 갖는 탓에
IMAX와 4DX의 배치가 다르다는 사실을 표현할 수 없었다.

README는 지점 목록 라벨을 `SCREENX`, `4DX`로 설명하지만 실제 응답은 `["특별관"]`
하나였다.

### 결정

`TheaterCategory` enum을 새로 두고 `TheaterType`이 이를 필드로 갖는다.

| 값 | 표시명 | 대분류 | rowCount | colCount | 총 좌석 |
|---|---|---|---|---|---|
| `STANDARD` | 일반관 | GENERAL | 8 | 10 | 80 |
| `IMAX` | IMAX | SPECIAL | 12 | 22 | 264 |
| `FOUR_DX` | 4DX | SPECIAL | 10 | 16 | 160 |
| `SCREEN_X` | SCREENX | SPECIAL | 10 | 20 | 200 |

- 대분류를 boolean이 아닌 enum으로 둔 이유: "특별관"이라는 **표시명**이 필요한 자리가
  생길 수 있다. boolean은 그 이름을 담을 곳이 없다.
- `TheaterCategory`는 `TheaterType`이 결정하는 값이라 컬럼으로 저장하지 않는다.
- `STANDARD`는 8×10을 그대로 유지했다. 예매·회차 테스트의 기대값(80석, row 9는 범위 초과)이
  이 배치에 걸려 있다.
- 저장 형태는 기존과 동일(`theater.theater_type varchar(20)`). `ddl-auto`가 운영 `create` /
  테스트 `create-drop`이라 마이그레이션이 필요 없었다.

### 변경 파일

| 파일 | 내용 |
|---|---|
| `TheaterCategory.java` | 신설. GENERAL/SPECIAL + 표시명 |
| `TheaterType.java` | 값 4개로 재정의, `category` 필드와 `isSpecial()` 추가 |
| `BranchService.java` | `!= STANDARD` → `isSpecial()`, 라벨 정렬 추가 |
| `BranchControllerTest.java` | 특별관 라벨 테스트를 IMAX+4DX 조합으로 교체 |
| `README.md` | TheaterType 표, 용어 분리·라벨 집계 서술 |

### 라벨 정렬

한 지점이 특별관을 여러 종류 보유하면 `specialTypes` 라벨 순서가 `GROUP BY` 결과 순서에
좌우된다. `TheaterType` 선언 순서로 정렬해 응답을 고정했다. 테스트는 4DX를 먼저 저장한 뒤
`["IMAX", "4DX"]`가 나오는지 확인한다.

### 확인

`./gradlew test` 53개 전부 통과. `remainingSeats == 80`과 좌석 범위 초과(row 9) 케이스가
통과하므로 STANDARD 배치 8×10은 보존되었다.

---

## 세션 2: 예매 선점 상태와 결제 흐름

리뷰 피드백 "결제가 완료되지 않았을 경우 좌석 점유는 일어나지만 예약 확정은 아닌 상태가
존재할 것"을 반영한다. 세션 0에서 남겨둔 `seats.clear()` 문제도 여기서 같이 정리했다.

### 상태 전이

| from | to | 트리거 | 좌석 | 기록 |
|---|---|---|---|---|
| — | `PENDING` | 좌석 선택 | 점유 | `expiresAt` = 선택 + 10분 |
| `PENDING` | `RESERVED` | 결제 성공 | 점유 유지 | `confirmedAt` |
| `PENDING` | `CANCELLED` | 결제 실패 / 사용자 취소 | 해제 | `cancelledAt` |
| `PENDING` | `EXPIRED` | 만료 시각 경과 | 해제 | — |
| `RESERVED` | `CANCELLED` | 취소, 상영 20분 전까지 | 해제 | `cancelledAt` |

거부되는 전이

| 시도 | 응답 |
|---|---|
| 확정된 예매 재결제 | 409 `RESERVATION_NOT_PENDING` |
| 만료된 선점 결제 | 409 `RESERVATION_EXPIRED` |
| 취소된 예매 재취소 | 409 `ALREADY_CANCELLED` |
| 상영 20분 이내 확정 예매 취소 | 409 `CANCEL_DEADLINE_PASSED` |
| 점유 중인 좌석 선택 | 409 `SEAT_ALREADY_RESERVED` |

**결제 실패는 좌석을 바로 놓는다.** 실패한 자리를 붙들고 재시도하게 두면 경쟁이 심한 회차에서
좌석 회전이 막힌다. 실제 CGV도 결제에 실패하면 좌석 선택부터 다시 진행한다.

**만료는 `CANCELLED`와 분리했다.** 사용자가 놓은 것과 시간이 지나 회수한 것은 원인이 다르고,
합치면 "이 좌석이 왜 풀렸나"를 되짚을 수 없다.

### 결정 1 — 취소 이력과 중복 방지

좌석 행을 지우지 않고 유니크 키에 해제 키를 넣는다.

```
UNIQUE (screening_id, row_num, col_num, release_key)

점유 중   release_key = 0
풀린 좌석 release_key = 자기 reservation_id
```

MySQL에 partial unique index가 없어 "점유 중인 행만 유일"을 직접 표현할 수 없다. 해제 값으로
예매 id를 쓰면 한 예매가 같은 좌석을 두 번 가질 수 없으므로 풀린 행끼리 충돌하지 않는다.
시각을 쓰면 같은 좌석이 동시에 해제될 때 충돌할 수 있다.

얻은 것: DB 유니크 제약이 그대로 최종 방어선으로 남으면서 어느 좌석을 얼마에 취소했는지가
남는다. 취소된 예매를 조회하면 `seats`와 `totalPrice`가 그대로 보인다.
치른 비용: `release_key = 0`이 "점유 중"이라는 게 도메인 언어가 아니라 주석이 필요하다.

점유 테이블을 따로 두는 안은 테이블이 늘고 두 테이블 동기화가 어긋날 수 있어 택하지 않았다.

### 결정 2 — 만료는 `expires_at` + lazy 처리

- 조회는 쿼리 조건으로 거른다.
  `release_key = 0 AND (status <> PENDING OR expires_at > :now)`
  취소·만료로 풀린 행은 `release_key`에서 이미 빠지고, 남는 예외가 만료 시각은 지났지만 아직
  정리되지 않은 선점이라 시각 조건을 더한다.
- 쓰기는 좌석을 잡기 직전에 그 회차의 만료된 선점을 실제로 해제한다. 유니크 인덱스는 만료
  시각을 모르므로 행을 놓아주지 않으면 시간이 지난 좌석도 다시 잡을 수 없다.
- 정리 범위는 요청된 회차 한 건으로 좁혔다.

스케줄러를 두지 않은 이유: 정확성은 위 두 경로로 이미 보장된다. 스케줄러는 "언젠가 정리된다"는
보조 수단일 뿐인데 시간 제어·테스트 비용만 늘어난다. 나중에 얹어도 이 로직은 그대로 쓴다.

**구현 중 걸린 것**: 해제(UPDATE)를 새 좌석(INSERT)보다 먼저 flush해야 한다. 한 번에
flush하면 Hibernate가 INSERT를 UPDATE보다 앞서 내보내 같은 좌석에서 유니크 충돌이 난다.
`ReservationService.releaseExpiredHolds()`에서 명시적으로 `flush()`를 부르는 이유다.

### 결정 3 — 동시성

- 막히는 지점은 `reservation_seat` INSERT 시 유니크 인덱스다.
- `saveAndFlush()`로 INSERT를 즉시 강제하고 `DataIntegrityViolationException`을
  `SEAT_ALREADY_RESERVED`(409)로 바꾼다.
- 트랜잭션 경계는 `create()` 하나다. 만료 정리 → 검증 → INSERT → flush가 그 안에서 일어난다.
  충돌 시 만료 정리까지 함께 롤백되지만 무해하다. 다음 요청이 다시 정리한다.
- pre-check는 경쟁이 없을 때 친절한 응답을 주기 위한 것이고 방어선이 아니다. 검사와 INSERT
  사이의 틈은 원리적으로 막을 수 없고 그 틈을 제약이 막는다.
- 비관적 락은 쓰지 않는다. 좌석 단위로 잠글 행이 없고(좌석 마스터 테이블이 없다),
  `Screening` 행을 잠그면 회차 단위로 직렬화되어 처리량이 크게 떨어진다.

### 결정 4 — 취소 기한은 엔티티에

`Reservation`이 `screening`을 참조하므로 `startAt`과 비교할 재료가 이미 있다. 규칙이 하나뿐이라
정책 객체는 과하다고 보고 `CANCEL_DEADLINE_MINUTES = 20` 상수를 엔티티에 뒀다.

선점(`PENDING`)에는 기한을 적용하지 않는다. 아직 확정 전이라 언제든 놓을 수 있어야 한다.

`now`는 서비스가 주입한다. 엔티티가 `LocalDateTime.now()`를 직접 부르면 "10분 뒤",
"상영 20분 전" 같은 상황을 테스트에서 만들 수 없다. 이를 위해 `Clock` 빈을 도입했다.

### 결정 5 — 권종별 가격

`AudienceType` enum이 할인율을 보유한다. `TheaterType`이 좌석 배치를 갖는 것과 같은 패턴이다.

| 값 | 표시명 | 할인율 | 기준가 14,000 기준 |
|---|---|---|---|
| `ADULT` | 일반 | 0% | 14,000 |
| `YOUTH` | 청소년 | 20% | 11,200 |
| `PREFERENTIAL` | 우대 | 50% | 7,000 |
| `SENIOR` | 경로 | 50% | 7,000 |

`screening.price`가 기준가이고 권종은 거기서 얼마를 깎는지만 안다. 가격표 테이블은 만들지
않았다. 명세에 가격 얘기가 없고 권종은 값이 고정된 소수다.

요청은 좌석마다 권종을 받는다. 화면은 인원을 먼저 고르지만, 좌석-권종 매핑이 없으면 좌석별
금액을 정할 수 없다. 실제 티켓에도 좌석마다 권종이 찍힌다.

### 결정 6 — 좌석 수와 인원 수 검증

좌석마다 권종이 붙으므로 불일치가 구조적으로 발생하지 않는다. 별도 검증 대신 총 좌석 수
상한만 DTO에서 `@Size(max = 8)`로 막았다. 요청 형식 검증이라 Bean Validation이 맞는 자리다.

### API

| 메서드 | 경로 | 구분 | 하는 일 |
|---|---|---|---|
| POST | `/api/reservations` | 수정 | 좌석 선점. `PENDING` 생성 + `expiresAt` |
| POST | `/api/reservations/{id}/payment` | 신규 | mock 결제. 성공 → 확정 / 실패 → 좌석 해제 + 402 |
| DELETE | `/api/reservations/{id}` | 수정 | 취소. 선점은 즉시, 확정은 상영 20분 전까지 |
| GET | `/api/reservations/{id}` | 수정 | 상태·시각 4종·좌석별 권종/금액 |
| GET | `/api/screenings/{id}/seats` | 수정 | 점유 판정에 미만료 선점 포함 |
| GET | `/api/screenings` | 수정 | 잔여좌석 집계를 같은 기준으로 |

### 계획과 달라진 점

- **만료된 선점을 결제하면 정리까지 하려 했으나 예외만 던진다.** `CustomException`이
  트랜잭션을 롤백시켜 같은 트랜잭션에서 한 해제가 사라지기 때문이다. 정리는 그 회차의 다음
  좌석 선점 요청이 맡고, 그 전까지도 조회 조건이 시각을 보므로 좌석은 이미 풀린 것으로 센다.
- **결제 실패 경로만 `@Transactional(noRollbackFor = CustomException.class)`를 쓴다.**
  실패를 402로 알리면서 좌석 해제는 남겨야 해서다.
- **`EXPIRED` 상태의 예매를 취소하면 `RESERVATION_EXPIRED`를 준다.** `ALREADY_CANCELLED`로
  뭉치면 메시지가 사실과 다르다.
- **좌석 라벨 변환을 `ReservationSeat.label()` 한 곳으로 모았다.** 세션 0에서 적어둔
  `ReservationResponse`와 `ScreeningService`의 중복이 이번에 양쪽 다 수정 대상이 되어 함께
  정리했다.

### 남은 것

- 회차 조회 응답 구조는 세션 3에서 재설계하므로 이번에는 점유 판정 기준만 맞췄다.
- 매점(`purchase` / `purchase_product`) 서술은 세션 5 대상이라 그대로 뒀다.

### 확인

`./gradlew test` 70개 통과 (예매 서비스 21 + 예매 API 22).

`README.md`에도 반영했다. ERD와 `reservation`/`reservation_seat` 컬럼 표,
`ReservationStatus`·`AudienceType` ENUM 설명, 중복 예매 방지·선점 만료 설계 배경,
한계 항목(취소 이력 → `release_key`의 의미와 만료 행 잔존).

---

## 세션 3: 회차 조회 API 재설계

세션 1의 `TheaterType`(종류가 좌석 배치를 보유)과 세션 2의 좌석 점유 구조를 전제로 한다.

### 문제

기존 `GET /api/screenings`는 평면 리스트 + 옵션 필터 3개(`movieId`/`branchId`/`date`)뿐이었다.
CGV는 진입점이 둘이고(영화 먼저 / 극장 먼저) 회차 목록 화면에는 상영관 종류 탭·날짜 탭·시간대
탭이 있으며, 회차는 지점 안에서 상영관 종류로 묶여 표시된다. 기존 API로는 이 중 어느 화면도
그릴 수 없었다.

### 결정 1 — 진입점 두 개를 한 API로

영화별 예매와 극장별 예매는 사용자가 어느 필터를 먼저 채웠는지만 다르고 둘 다 "조건에 맞는
회차 목록"으로 수렴한다. 별도 API로 두면 같은 쿼리가 파라미터 이름만 바꿔 두 벌이 된다.

선택 화면을 받치는 보조 API는 따로 뒀다. 지역별 극장 수와 예매율 순 영화 목록이다.

### 결정 2 — 시그니처와 응답 구조

```
GET /api/screenings
  ?movieId=1
  &branchIds=1,2,3      # 극장 복수 선택. 반복 파라미터도 같게 바인딩된다
  &date=2024-06-01      # 생략하면 오늘
  &theaterType=IMAX     # 생략하면 전체 탭
  &timeSlot=EVENING     # 생략하면 전체 탭
```

응답은 화면 그대로 **지점 → 상영관 종류 → 회차** 2단 그룹이다.

- 지점은 이름 오름차순(화면과 같다), 상영관 종류는 `TheaterType` 선언 순서, 회차는 시작 시각순
- 상영관 종류는 `varchar`로 저장돼 DB가 정렬하면 알파벳순이 된다. 선언 순서를 지키려고
  `TreeMap`으로 서비스에서 다시 묶는다
- 회차 카드에 영화를 남겼다. 극장부터 고르는 경로에서는 영화를 고르기 전까지 여러 편이 섞인다
- **오늘이면 이미 시작한 회차를 뺀다.** 예매할 수 없는 카드를 남길 이유가 없다.
  조회 시작 시각을 `max(구간 시작, 현재)`로 잡아 지난 날짜·지난 시간대까지 한 식으로 처리한다
- 날짜 탭 6일치는 클라이언트가 오늘부터 계산한다. 서버가 따로 줄 정보가 없다

**구현 중 걸린 것**: JPQL은 빈 컬렉션 바인딩을 허용하지 않아 `IN :branchIds`를 조건부로 끌 수
없다. `filterByBranch` 플래그로 조건 자체를 끄고 파라미터에는 자리만 채웠다.

### 결정 3 — 잔여석은 매 조회 계산

비정규화 컬럼은 세션 2 구조와 양립하지 않는다. 선점 만료가 시각 의존이라 10분이 지나면 아무도
손대지 않아도 잔여석이 늘어야 하는데 컬럼은 그 순간을 모른다. 반영하려면 배치가 필요하고
그건 범위 밖이다.

세션 2의 `countOccupiedByScreeningIds()`를 그대로 쓴다. 회차 id를 모아 `GROUP BY` 한 방이라
N+1이 아니다. 총석은 `TheaterType.getTotalSeatCount()`이고 카드에 `soldOut`을 같이 내린다.

### 결정 4 — 지역 목록 API 신설

`GET /api/branches/regions`. `Region`이 enum이라 지역 테이블이 없으므로 `Branch`를 지역으로
묶어 세고(`GROUP BY` 한 번), 응답은 enum 선언 순서로 만든다. 집계에 없는 지역은 0으로 채워
극장이 없어도 탭 구성이 흔들리지 않게 했다. 목록에서 빼는 상태(`CLOSED`)는 지점 목록과 같은
기준을 따른다.

### 결정 5 — 시간대 경계는 `TimeSlot` enum 한 곳

| 값 | 표시명 | 구간 |
|---|---|---|
| `MORNING` | 오전 | 00–12 |
| `AFTERNOON` | 오후 | 12–18 |
| `EVENING` | 18시 이후 | 18–23 |
| `LATE_NIGHT` | 심야 | 23–24 |

- "전체"는 enum 값이 아니라 **파라미터 미지정**으로 표현한다. `ALL`을 두면 시·종 시각이 없는
  값이 하나 섞여 `startOn`/`endOn`이 의미를 잃는다
- enum이 `startOn(date)` / `endOn(date)`로 경계를 돌려주고 서비스는 그대로 쿼리에 넘긴다.
  컨트롤러·서비스에 시각 비교 분기를 만들지 않는다
- 자정을 넘기는 회차는 다음 날짜 탭에 잡힌다. 심야는 해당 날짜의 23~24시만 본다는 단순화다

### 결정 6 — 예매율은 조회 시점 집계

배치도 비정규화 컬럼도 두지 않는다. **확정(`RESERVED`) 좌석 수**를 `GROUP BY movie`로 한 번에
세고 서비스에서 정렬한다. 동점은 최신 개봉순으로 가른다.

- 선점(`PENDING`)은 아직 결제 전이라 세지 않는다
- 취소·만료분은 `status`가 `RESERVED`가 아니게 되므로 자동으로 빠진다
- `MovieResponse.reservedSeatCount`로 정렬 근거를 드러냈다

### API

| 메서드 | 경로 | 구분 | 하는 일 |
|---|---|---|---|
| GET | `/api/screenings` | 수정 | 통합 회차 조회. 5개 필터, 2단 그룹 응답 |
| GET | `/api/branches/regions` | 신규 | 지역 탭 + 지역별 극장 수 |
| GET | `/api/movies` | 수정 | 예매율 내림차순, `reservedSeatCount` 추가 |
| GET | `/api/screenings/{id}/seats` | 유지 | 세션 2에서 점유 기준을 이미 맞췄다 |

삭제된 엔드포인트는 없다. 교차 필터(영화로 극장 거르기, 극장으로 영화 거르기)는 범위에서 뺐다.

### 범위에서 뺀 것

- **상영 포맷(2D/3D)**은 새 컬럼을 만들지 않고 `TheaterType`으로 대체했다. 화면의 상영관 종류
  탭과 기준이 같아져 오히려 일관된다
- 미사용 상태였던 `ScreeningRepository.findByTheaterIdOrderByStartAtAsc()`를 제거했다

### 확인

`./gradlew test` 86개 통과 (회차 서비스 13 + 회차 API 12 + 영화 API 5 + 지점 API 12).

---

## 세션 3-1: 조회 쿼리 정리

PR 리뷰 피드백 "`create()`를 한 번 실행했을 때 나가는 SQL을 비교하고 불필요한 SELECT가 없는지
확인하라"에 대한 처리. 세션 4 착수 전에 끝냈다.

### 문제

`create()`는 `findByIdWithTheaterType()`으로 `theater`만 fetch join 하는데, 응답을 만드는
`ReservationResponse.ScreeningSummary.from()`이 `movie.title`과 `theater.branch.name`을 읽는다.
둘 다 LAZY `@ManyToOne`이라 **DTO 변환 시점에 프록시 초기화 SELECT가 두 건 더 나갔다.**
서비스 코드만 보면 보이지 않고 로그를 찍어야 드러나는 종류의 낭비다.

좌석 2개 예매 기준 SQL 9건 → **7건**.

| # | SQL | 비고 |
|---|---|---|
| 1 | SELECT screening + movie + theater + branch | 조인 확장 |
| 2 | SELECT users | 존재 검증(`USER_NOT_FOUND`)의 근거라 남는다 |
| 3 | SELECT 만료 선점 | 유니크 인덱스가 만료 시각을 모른다 |
| 4 | SELECT 점유 좌석 | pre-check |
| 5-7 | INSERT reservation 1 + reservation_seat 2 | |
| ~~+2~~ | ~~SELECT movie / SELECT branch~~ | **제거** |

### 결정 1 — 회차 조회를 용도별로 둘로 나눈다

기존 쿼리를 넓히지 않고 `ScreeningRepository.findByIdWithDetails()`를 새로 뒀다.
좌석 조회(`getSeats()`)는 `theater.theaterType`(enum 컬럼)만 읽으므로 movie·branch를 더하면
그쪽이 over-fetch가 된다. 한 쿼리로 합치면 두 화면 중 하나는 반드시 손해를 본다.

이름이 내용과 어긋나 있던 `findByIdWithTheaterType`은 `findByIdWithTheater`로 바꿨다.
세션 1-1에서 `TheaterType`이 enum 컬럼이 된 뒤로 "타입을 함께 가져온다"는 뜻이 사라졌다.

### 결정 2 — fetch join 쿼리의 `DISTINCT` 제거

Hibernate 6부터 컬렉션 fetch join의 엔티티 중복은 항상 메모리에서 제거되고, HQL의 `DISTINCT`는
SQL로 그대로 전달된다(`passDistinctThrough` 옵션 자체가 없어졌다). 중복 제거 효과는 그대로인데
조인 결과 전체에 대한 SQL `DISTINCT` 비용만 남으므로 뺐다.

### 결정 3 — 취소는 전용 쿼리를 쓴다

`cancel()`은 좌석 해제와 `screening.startAt` 비교만 하고 응답을 만들지 않는다.
`findByIdWithDetails`를 그대로 쓰면 영화·지점까지 조인해 읽지도 않을 컬럼을 끌고 온다.
`findByIdWithSeats`(screening + seats)를 따로 뒀다. 쿼리 수는 1회로 같고 조인 폭만 줄었다.

### 결정 4 — `user`는 조인하지 않는다 (가정이 틀렸던 부분)

당초 `JOIN FETCH r.user`가 필요하다고 봤다. 필드 접근 매핑이면 프록시의 id getter가 단축되지
않아 `user.getId()`가 초기화를 부를 것이라 판단했기 때문이다. **실측 결과 틀렸다.**
조인을 빼도 단건 조회 SQL은 1건 그대로였다. Hibernate는 필드 접근이어도 식별자 getter를
가로채 초기화 없이 값을 돌려준다.

응답이 사용자를 id로만 쓰므로 조인을 뺐다. 이름 같은 다른 필드를 응답에 실으면 그때 다시
fetch join을 더해야 하고, 그 사실을 쿼리 위 주석으로 남겼다.

### 회귀 테스트

`ReservationQueryCountTest`. 테스트 설정에만 `hibernate.generate_statistics=true`를 켜고
`Statistics.getPrepareStatementCount()`로 SQL 수를 센다.

- `create()`(좌석 2개) = 7건, `getById()` = 1건
- 수정 전 코드에서 `create()`가 9건으로 실패하는 것을 먼저 확인하고 고쳤다

DTO에 필드가 늘어 LAZY 초기화가 다시 끼어들면 이 테스트가 잡는다. 로그를 눈으로 대조하지
않아도 되게 만드는 것이 목적이다.

### 검토했으나 하지 않은 것

- **`userRepository.findById()` → `getReferenceById()`** — 존재 검증을 잃는다. FK 위반이
  `DataIntegrityViolationException`으로 올라와 `SEAT_ALREADY_RESERVED`로 잘못 번역된다.
- **pre-check 쿼리를 요청 좌석으로 좁히기** — 가져오는 행의 상한이 총 좌석 수(최대 264)이고
  `screening_id` 인덱스 한 번의 스캔이다. `getSeats()`와 공유 중인 쿼리를 쪼갤 만한 이득이 없다.
- **INSERT 배치(`hibernate.jdbc.batch_size`)** — 좌석 PK가 `IDENTITY`라 생성 키를 행마다
  받아야 해서 배치가 걸리지 않는다.
- `ScreeningService.search()`, `MovieService`, `BranchService`는 목록 + `GROUP BY` 집계
  2쿼리 구조라 이미 N+1이 없다. `default_batch_fetch_size: 100`도 이미 설정돼 있다.

### 확인

`./gradlew test` 88개 통과 (기존 86 + 신규 2). 엔티티·DTO·컨트롤러와 API 응답은 그대로다.

---

## 세션 3-2: 예매 단건 조회 over-fetch 정리

세션 3-1이 **쿼리 수**를 줄였다면 이번은 한 건당 **읽는 양**을 줄인다.
`getById()`가 `findByIdWithDetails()`로 엔티티 6종을 통째로 가져오고 있었다.

### 문제

응답 `ReservationResponse`가 쓰는 값과 SELECT가 읽는 컬럼의 차이:

| 엔티티 | SELECT | 응답이 쓰는 것 |
|---|---|---|
| reservation | 9 | 7 |
| screening | 8 | 3 (id, startAt, endAt) |
| movie | 9 | 1 (title) |
| theater | 6 | 1 (name) |
| **branch** | 9 (**description TEXT** + image_url 포함) | 1 (name) |
| reservation_seat | 10 × N | 4 × N |

두 낭비가 겹친다.

1. **컬럼 폭** — `Branch.description`은 교통·주차 안내를 담는 TEXT(최대 64KB)인데
   응답은 지점 **이름**만 쓴다.
2. **행 증폭** — `LEFT JOIN FETCH r.seats`는 좌석 수만큼 행을 만든다. Hibernate가
   엔티티를 메모리에서 합치기 전에 좌석 1개당 헤더 41개 컬럼이 한 번씩 전송되므로,
   같은 description을 좌석 수(최대 8)만큼 읽는다.
3. 덤으로 영속성 컨텍스트에 엔티티 `5 + N`개와 더티 체킹 스냅샷이 남는다.
   조회는 변경 감지가 필요 없는데도 그렇다.

### 결정 1 — 조회는 엔티티를 쓰지 않는다

`ReservationDetailRow`(record, 좌석 1행) + `ReservationRepository.findDetailRowsById()`
생성자 표현식 프로젝션. `ReservationResponse.of(rows, now)`가 조립한다.
헤더 값은 행마다 반복되지만 이제 전부 스칼라다.

```
before: 41개 컬럼(TEXT 포함) × N행 + 엔티티 5+N개
after : 17개 컬럼            × N행 + 엔티티 0개
```

SQL은 1건 그대로다. `r.user.id`는 FK 컬럼이라 users 조인이 생기지 않는다(3-1 결정 4와 같은 근거).
좌석 정렬도 `ORDER BY`로 DB가 하므로 DTO의 `Comparator`가 이 경로에서는 빠진다.

### 결정 2 — 좌석 조인은 INNER JOIN

좌석은 `@NotEmpty @Size(max = 8)`이라 예매에 항상 1개 이상 있다. INNER JOIN이어도 예매가
있으면 행이 비지 않으므로, **빈 결과 = 예매 없음**이 되어 `RESERVATION_NOT_FOUND` 판정이
행 수 하나로 끝난다. 취소·만료로 풀린 좌석도 이력으로 응답에 남아야 하므로 `release_key`
조건은 넣지 않는다(기존과 동일).

### 결정 3 — 변경 경로(`create`, `pay`)는 엔티티를 유지한다

상태 전이는 관리 상태 엔티티가 있어야 한다. `pay()`를 프로젝션으로 바꾸면 변경용 조회와
응답용 조회가 나뉘어 SELECT가 1건에서 2건이 된다. 결제는 예매당 한 번뿐이라 이득이 없다.
`findByIdWithDetails`는 이제 `pay()` 전용이다.

만료 판정(`PENDING && now >= expiresAt`)은 프로젝션에 엔티티가 없어
`Reservation.isExpired()`를 부를 수 없다. `ReservationResponse`의 private static
`resolveStatus()`로 빼고 엔티티 경로도 같은 메서드를 쓰게 했다. 엔티티는 건드리지 않았다.

### 회귀 테스트

`ReservationQueryCountTest.예매_단건_조회는_SQL_1회로_끝난다`에
`statistics.getEntityLoadCount()`가 0이라는 단언을 더했다. 변경 전에는 6(= 5 + 좌석 1)이
나오는 것을 먼저 확인했다. SQL **수**는 기존 단언이, 읽는 **양**은 이 단언이 지킨다.
조회가 다시 엔티티를 타면 여기서 걸린다.

실제 SQL에서 `description` / `image_url` / `director` / `age_rating`이 사라진 것을 확인했다.

### 검토했으나 하지 않은 것

- **`pay()`도 프로젝션화** — 결정 3. SELECT 1→2건.
- **`Branch.description`에 `@Basic(fetch = LAZY)`** — 바이트코드 인핸스먼트가 있어야 동작하고
  엔티티 변경이 필요하다. 지점 상세 화면은 어차피 description을 쓰므로 전역으로 미루는 것은
  또 다른 곳에서 추가 SELECT를 만든다.
- **조회를 헤더/좌석 2쿼리로 분리** — 헤더 반복이 사라지지만 SQL이 2건이 된다. 반복되는 값이
  스칼라뿐이라 남은 중복의 크기가 작다.

### 확인

`./gradlew test` 88개 통과. 엔티티·컨트롤러·API 응답은 그대로다.

---

## 세션: 좌석 경합 시 커넥션 풀 보호 + 데드락 제거

### 배경

"같은 좌석에 요청이 몰리면 유니크 제약을 확인하는 과정에서 대기가 생기는가,
그때 커넥션과 다른 요청의 응답 시간은 어떻게 되는가"를 확인하다 시작했다.

InnoDB는 중복 키 INSERT를 즉시 1062로 거절하지 않는다. 선행 트랜잭션이 끝날 때까지
S 락을 걸고 블로킹한다. 그 대기 스레드는 **커넥션을 쥔 채로** 기다린다.
설정이 전부 기본값이어서 `maximumPoolSize=10`, `connectionTimeout=30초`,
`innodb_lock_wait_timeout=50초` 였다. 좌석 하나의 경합으로 커넥션 10개가 묶이면
11번째 요청부터는 엔드포인트와 무관하게 30초 뒤 죽는다. 락 한도가 커넥션 한도보다 길어서
**경합과 무관한 API가 경합 중인 API보다 먼저 죽는** 역전이 있었다.

### 결정 1 — 비관적 락은 넣지 않는다

이 스키마에는 Seat 테이블이 없어서 예매 전 좌석에는 **잠글 행 자체가 없다.**
결국 `Screening` 행에 `FOR UPDATE`를 거는 수밖에 없는데, 그건 회차 전체를 직렬화하는 것이라
지금의 좌석 단위 유니크 제약보다 처리량이 훨씬 나쁘다.
유니크 제약에 맡기는 방식 자체는 이 설계에 맞는 선택이다.
고칠 것은 락 전략이 아니라 **락의 범위와 커넥션 점유**였다.

### 결정 2 — 만료 선점 정리를 "요청한 좌석을 막고 있는 것"으로 좁힌다

`findExpiredHolds` → `findExpiredHoldsBlocking`. 회차의 만료 선점을 전부 푸는 대신
요청한 좌석을 실제로 점유 중인 선점만 고른다.

전에는 A5를 고르는 요청이 J12의 만료 선점까지 UPDATE했다. 그 행 락이 예매 커밋까지
유지되므로, 같은 회차를 골랐을 뿐인 다른 좌석 요청들이 서로를 기다렸다.
**경합 단위가 좌석이 아니라 회차였다.**

(행, 열) 쌍을 IN 절에 넣는 방법이 DB마다 달라 `rowNum * 100 + colNum` 스칼라 하나로 접었다.
상영관 종류의 최대 열 수가 22라 100진 자리에서 겹치지 않는다. 이 키를 만들려면 좌석이
범위 안이어야 하므로 정리를 범위·중복 검증 **뒤로** 옮겼다(3→5번). 검증이 먼저 오는 순서가
읽기에도 낫다.

#### 검토했으나 하지 않은 것: 별도 트랜잭션(REQUIRES_NEW) 분리

정리를 `REQUIRES_NEW` 빈으로 떼어내 락을 즉시 놓게 하는 안을 먼저 구현했다가 되돌렸다.
`ReservationControllerTest.만료된_선점의_좌석은_다시_잡을_수_있다`가 409로 깨졌다.
별도 트랜잭션은 다른 커넥션에서 돌기 때문에 `@Transactional` 통합 테스트의 **미커밋 데이터를
볼 수 없다.** 테스트만의 문제가 아니라 "정리가 호출자 트랜잭션에 참여하지 않는다"는
실제 의미 변화다.

게다가 REQUIRES_NEW는 락 **보유 시간**만 줄일 뿐 락 **범위**는 그대로 둔다.
결정 2가 범위를 직접 줄이므로 그쪽이 본질이었다.

### 결정 3 — 좌석을 정렬해서 INSERT한다

`orderedSeats()`로 (행, 열) 오름차순 정렬 후 `addSeat`을 부른다.
Hibernate는 `hibernate.order_inserts` 미설정 시 컬렉션 순서대로 INSERT하고,
INSERT 순서가 곧 락 획득 순서다. 전에는 `[A1,A2]` 요청과 `[A2,A1]` 요청이 서로를 물고 도는
순환 대기를 만들었다.

`ReservationResponse.from`도 정렬하지만 그건 **응답 표시용**이고, 이건 **INSERT 순서**다.
둘은 다른 문제다.

**실측** — 정렬을 빼고 좌석이 엇갈린 동시 요청 6건을 던지면 성공이 **0건**이다.
전원이 서로를 죽인다. 예외가 새는 정도가 아니라 기능이 무너진다.

### 결정 4 — 데드락·락 타임아웃을 409로 매핑한다

`SEAT_RESERVATION_CONFLICT` 추가. 데드락(1213)과 락 타임아웃(1205)은 Spring에서
`ConcurrencyFailureException` 계열로 번역되어 기존 `catch (DataIntegrityViolationException)`에
**걸리지 않았다.** 그대로 500이 나갔다.

`SEAT_ALREADY_RESERVED`로 뭉치지 않은 이유: 락 타임아웃은 좌석이 팔렸다는 뜻이 아니라
**판정하지 못했다**는 뜻이다. 재시도하면 성공할 수 있어서 안내 문구가 달라야 한다.
결정 3으로 데드락 경로는 사실상 사라지지만 안전망으로 남긴다.

### 결정 5 — 커넥션 풀이 인질로 잡히지 않게 한다

| 항목 | 전 | 후 |
|---|---|---|
| `innodb_lock_wait_timeout` | 50초 | 3초 (JDBC URL `sessionVariables`) |
| Hikari `maximum-pool-size` | 10 | 20 |
| Hikari `connection-timeout` | 30초 | 3초 |
| `open-in-view` | true | false |

락 한도를 커넥션 한도보다 길게 두지 않는 것이 핵심이다. 그래야 무관한 API가 먼저 죽는
역전이 사라진다. `connection-init-sql`이 아니라 JDBC URL로 넣은 이유는 H2 테스트가
그 변수를 모르기 때문이다(테스트 yaml이 datasource를 통째로 덮어쓴다).

`open-in-view: false`는 조회가 전부 fetch join 또는 DTO 프로젝션으로 정리된 뒤라 안전하다.
(이전 세션의 조회 쿼리 정리가 선행 조건이었다.)

### 회귀 테스트

`ReservationConcurrencyTest` 추가. 스레드마다 트랜잭션이 따로 열려야 해서
`ControllerIntegrationTest`(@Transactional)를 상속하지 않고 `@AfterEach`에서 직접 정리한다.

1. 같은 좌석 6건 동시 → 성공 정확히 1건, 실패는 전부 매핑된 CustomException
2. 좌석 순서가 엇갈린 6건 동시 → 성공 1건, 점유 좌석 2개 (수정 전 성공 0건으로 실패 확인)
3. 만료된 선점이 잡고 있던 좌석을 동시 요청 2건이 다시 가져감 → 둘 다 성공

테스트 DB가 H2라 InnoDB의 duplicate-key 블로킹 타이밍까지 재현하지는 못한다.
H2도 행 락 타임아웃 시 `CannotAcquireLockException`을 던져 예외 매핑 검증에는 충분하다.

### 확인

`./gradlew test` 94개 통과(기존 88 + 신규 6). 엔티티는 건드리지 않았다.

### MySQL 실환경 확인

테스트는 H2라 `application.yaml` 설정 4개와 새 JPQL이 한 번도 실행되지 않았다
(`src/test/resources/application.yaml`이 main 설정을 통째로 가린다).
MySQL 8.0.45에 앱을 띄워 직접 확인했다. 기준값은 전역 `innodb_lock_wait_timeout=50`,
격리수준 `REPEATABLE-READ`였다.

**새 JPQL** — 생성 SQL이 의도대로 번역됐다.

```sql
and exists(select 1 from reservation_seat rs1_0
  where rs1_0.reservation_id=r1_0.reservation_id
    and rs1_0.screening_id=?
    and rs1_0.release_key=0
    and ((rs1_0.row_num*100)+rs1_0.col_num) in (?))
```

바인딩 값은 `101`(= 1×100 + 1)이었다.

**범위 축소가 실제로 먹는다** — 1행1열과 5행5열에 선점을 만들고 **둘 다** 만료시킨 뒤
1행1열만 재요청했다.

| 예매 | 좌석 | 요청 후 status | release_key | updated_at |
|---|---|---|---|---|
| 1 | A1 | PENDING → **EXPIRED** | 0 → 1 | 갱신됨 |
| 2 | E5 | **PENDING 유지** | **0 유지** | **요청 전과 동일** |

만료 시각이 지났어도 **요청하지 않은 좌석의 선점은 건드리지 않는다.**
전에는 둘 다 풀면서 E5 행에도 UPDATE 락을 걸었다.

**락 타임아웃 + 예외 매핑** — MySQL 세션에서 같은 좌석 행을 INSERT하고 12초간 커밋하지 않은
상태로 앱에 같은 좌석을 요청했다.

- 소요 **3.32초** (전역 50초도, 세션이 쥔 12초도 아님) → `sessionVariables`가 먹었다
- **409 `SEAT_RESERVATION_CONFLICT`** → `ConcurrencyFailureException` catch가 동작.
  이 catch가 없었으면 500이었다

이 측정으로 "InnoDB는 중복 키 INSERT를 즉시 거절하지 않고 커넥션을 쥔 채 블로킹한다"는
전제도 실증됐다.

**`open-in-view: false`** — GET 11개(지역·키워드 필터, 지점 상세, 회차 목록·좌석, 예매 단건)와
쓰기 경로(결제·취소)를 모두 태웠다. 전부 2xx, 로그에 `LazyInitializationException` **0건**.

**풀이 인질로 잡히지 않는다** — 같은 좌석으로 동시 30건을 던지면서 무관한 `/api/movies`를 쟀다.

- 부하 중 앱 DB 커넥션 **20개** (`maximum-pool-size: 20` 적용 확인)
- `/api/movies` 계속 **200, 8~9ms** — head-of-line blocking 없음
- 예매 30건 결과 **201 정확히 1건 + 409 29건**, 500 **0건**
- 전 구간 `SQLTransientConnectionException` **0건**

전체 로그 집계는 `SEAT_ALREADY_RESERVED` 58건, `SEAT_RESERVATION_CONFLICT` 1건이었다.
경합 대부분은 pre-check와 유니크 제약에서 깔끔히 걸러지고, 락 타임아웃은 위 인위적 시나리오
하나뿐이었다.

---

## 세션 4: 찜 API

`BranchLike`/`MovieLike` 엔티티와 리포지토리만 있고 API가 없었다. CGV 극장 카드·상세와 영화의
별 아이콘(누르면 찜, 다시 누르면 해제)을 받칠 API를 만든다. **엔티티는 수정하지 않았다.**
유니크 제약(`uk_branch_like_user_branch`, `uk_movie_like_user_movie`)이 이미 걸려 있었다.

극장 찜과 영화 찜은 커밋을 나눈다. 공통 작업(ErrorCode, 예외 핸들러, 아래 결정)은 극장 찜에 포함했다.

### 결정 1 — 토글 대신 POST(등록) + DELETE(해제)

토글은 "현재 상태를 뒤집어라"라서 결과가 서버 상태에 의존한다. 응답이 유실돼 재시도하면
찜 → 해제로 되돌아가므로 클라이언트가 재시도해도 되는지 판단할 수 없다. POST/DELETE는 요청이
**도달할 최종 상태**를 말하므로 같은 요청을 몇 번 보내도 결과가 같다. 별 아이콘 UI는 현재 상태를
이미 알고 있어 어느 쪽을 부를지 고르는 비용이 없다.

### 결정 2 — 이미 찜한 걸 찜 / 안 한 걸 해제 → 둘 다 200

요청한 최종 상태가 이미 성립해 있으면 목표 달성으로 본다. 409를 주면 클라이언트가 할 수 있는 건
"다시 동기화"뿐이고 재시도 안전성도 깨진다.

- POST는 대상·사용자 존재를 검증한다(404). FK 위반도 `DataIntegrityViolationException`이라
  검증이 없으면 없는 극장이 경합 충돌로 잘못 번역된다(세션 3-1과 같은 근거).
- DELETE는 검증하지 않는다. 없는 극장이면 찜도 없으므로 "찜 아님"이 이미 성립한다.
- 폐관 극장 찜은 막지 않는다. 상세 화면이 폐관도 보여주는 정책이고, 찜 목록에 상태를 싣는다.
- POST가 201이 아닌 200인 이유: 새로 만들었는지가 응답에 의미 없고(멱등) 찜 행의 URI도 노출하지 않는다.

### 결정 3 — 중복 방지는 두 곳에서 막힌다

| 상황 | 막는 곳 | 응답 |
|---|---|---|
| 순차 중복 (재시도, 느린 더블클릭) | `existsBy...` pre-check | 200 (no-op) |
| 진짜 동시 (INSERT 두 건이 겹침) | 유니크 제약 → `DataIntegrityViolationException` | 409 `LIKE_REQUEST_CONFLICT` |
| 락 대기 타임아웃 | `ConcurrencyFailureException` | 409 `LIKE_REQUEST_CONFLICT` |

**동시 경합에서 진 쪽에 200을 줄 수 없다.** flush 중 제약 위반이 나면 Hibernate 세션과 바깥
트랜잭션이 rollback-only가 된다. 예외를 삼키고 정상 반환하면 커밋에서 `UnexpectedRollbackException`
(500)이 난다. 별도 트랜잭션(REQUIRES_NEW / NOT_SUPPORTED)으로 빼면 `@Transactional` 통합 테스트의
미커밋 데이터를 못 본다(좌석 경합 세션에서 겪은 문제).

그래서 `SEAT_RESERVATION_CONFLICT`와 같은 성격으로 뒀다. "실패가 아니라 판정 못 함, 재시도하면
성공". 재시도는 pre-check에 걸려 200으로 수렴한다. 극장·영화 공용 코드 하나다.

해제는 JPQL 벌크 DELETE다. 파생 `deleteBy...`는 SELECT 후 엔티티별로 지워서, 동시 해제로 이미
사라진 행을 만나면 낙관적 락 예외가 난다. 벌크 DELETE는 0행이어도 정상이다. SELECT도 없어진다.

### 결정 4 — `userId`는 세 엔드포인트 모두 쿼리 파라미터

DELETE 본문은 HTTP 의미가 정의돼 있지 않아 일부 클라이언트·프록시가 버린다. POST만 본문으로 받으면
다음 주에 비워질 Request DTO를 새로 만드는 셈이라 셋을 맞췄다. Security 이후 파라미터만 걷어낸다.

이 과정에서 `MissingServletRequestParameterException`이 처리되지 않아 필수 파라미터 누락이 **500**으로
나가던 것을 발견했다. `GlobalExceptionHandler`에서 400 `INVALID_INPUT_VALUE`로 매핑했다.

### 결정 5 — 내 찜 목록 API를 둔다

결정 6에서 목록 응답에 찜 여부를 넣지 않으므로, 이게 없으면 찜 상태를 **읽을 경로가 없다.**
CGV 극장 탭의 "자주 가는 CGV" 영역과 같다.

- 최근 찜한 순(`id DESC`), `JOIN FETCH` 1쿼리
- `status`를 싣는다. 찜한 뒤 폐관된 극장을 조용히 빼면 사용자는 왜 사라졌는지 모른다
- 없는 사용자면 빈 배열이 아니라 `USER_NOT_FOUND`. 잘못된 id가 "찜 없음"으로 숨지 않게
- DTO 프로젝션(세션 3-2)은 쓰지 않았다. 표시명이 enum 메서드라 생성자 표현식에 못 넣어 Row record가
  하나 더 필요하고, 찜 수는 많아야 수십 건이다. 지점 목록 API도 엔티티를 그대로 읽는다

### 결정 6 — 목록 응답에 "내가 찜했는지"는 이번에 넣지 않는다

넣으려면 공개 GET(`/api/branches`, `/api/movies`)에 `userId`를 붙여야 하고, 다음 주 principal로
바뀌면서 시그니처가 한 번 더 바뀐다. 익명이면 `liked`가 null/false 삼중 상태가 된다. 지금은 결정 5의
목록으로 클라이언트가 찜 id 집합을 한 번 받아 별을 칠한다(요청 1회 추가, N+1 없음).

Security 이후 넣을 때의 방법: 목록 id를 모아
`SELECT bl.branch.id FROM BranchLike bl WHERE bl.user.id = :userId AND bl.branch.id IN :ids`
한 번 → `Set<Long>` → `contains`. `specialTypesByBranchId()`와 같은 "id 모아 한 방" 패턴이라 쿼리는 1건만 는다.

### API (극장)

| 메서드 | 경로 | 요청 | 응답 | 에러 |
|---|---|---|---|---|
| POST | `/api/branches/{branchId}/likes` | `?userId=` | 200 (이미 찜이어도) | 400 · 404 `USER_NOT_FOUND` · 404 `BRANCH_NOT_FOUND` · 409 `LIKE_REQUEST_CONFLICT` |
| DELETE | `/api/branches/{branchId}/likes` | `?userId=` | 200 (찜 없어도) | 400 |
| GET | `/api/branches/likes` | `?userId=` | 200 `List<BranchLikeResponse>` | 400 · 404 `USER_NOT_FOUND` |

`/api/branches/likes`는 리터럴 경로라 `/{id}`보다 우선한다(`/regions`와 같다).
찜 API는 `BranchLikeController`/`BranchLikeService`로 분리했다. 조회 전용인 `BranchService`에
쓰기와 `UserRepository` 의존이 섞이지 않게.

### 회귀 테스트 (극장)

- `BranchLikeControllerTest` 10개 — 등록·목록, 중복 등록 200 + 행 1개, 해제, 찜 없는 해제 200,
  남의 찜은 안 지워짐, 404 3종, `userId` 누락 400, 최근 순 + 폐관 극장 상태 표시
- `BranchLikeConcurrencyTest` 2개 — 같은 찜 6건 동시 → 행 정확히 1개, 실패는 전부
  `LIKE_REQUEST_CONFLICT`. 진 요청도 재시도하면 성공. 로그에서 유니크 제약 위반이 실제로 발생해
  catch 경로를 탔음을 확인했다

### 확인 (극장)

`./gradlew test` 106개 통과(기존 94 + 신규 12).

### API (영화)

결정 1~6을 그대로 따른다. 극장 찜과 구조가 대칭이다.

| 메서드 | 경로 | 요청 | 응답 | 에러 |
|---|---|---|---|---|
| POST | `/api/movies/{movieId}/likes` | `?userId=` | 200 (이미 찜이어도) | 400 · 404 `USER_NOT_FOUND` · 404 `MOVIE_NOT_FOUND` · 409 `LIKE_REQUEST_CONFLICT` |
| DELETE | `/api/movies/{movieId}/likes` | `?userId=` | 200 (찜 없어도) | 400 |
| GET | `/api/movies/likes` | `?userId=` | 200 `List<MovieLikeResponse>` | 400 · 404 `USER_NOT_FOUND` |

`MovieLikeResponse`는 `movieId`, `title`, `genre`, `releaseDate`, `ageRating`, `likedAt`.
예매율(`reservedSeatCount`)은 싣지 않았다. 찜 목록은 정렬 기준이 찜한 시각이라 쓸 곳이 없고,
넣으면 `GROUP BY` 집계 쿼리가 하나 더 붙는다.

### 회귀 테스트 (영화)

- `MovieLikeControllerTest` 10개. 극장 찜과 같은 항목에서 폐관 상태 대신 최근 순만 확인한다
- 동시성 테스트는 두지 않았다. 서비스 구조와 제약 방식이 극장과 같아 `BranchLikeConcurrencyTest`가
  같은 경로를 검증한다

### 확인 (영화)

`./gradlew test` 116개 통과(극장 찜까지 106 + 신규 10). 엔티티는 건드리지 않았다.

---

## 세션 5: 매점 구매 API

`store` 도메인에 엔티티·리포지토리만 있었다. 과제 명세(극장별 재고, 공통 메뉴, 환불 없음)와
운영진 확인 불변식 **"재고는 어떤 시점에도 1 이상"**을 반영해 메뉴 조회·구매·구매 내역 API를 만들었다.

이미 정해져 있어 유지한 것: 메뉴는 `Product`(공통), 재고는 `Stock`(극장 × 상품),
`Purchase`-`PurchaseProduct` 헤더-디테일, 구매 시점 가격의 `unitPrice` 복사, 취소 API 없음.

### 결정 1 — 재고 불변식은 엔티티가 1차, DB CHECK가 최종선

`Stock`만 수정했다(`Purchase`·`PurchaseProduct`·`Product`는 그대로).

| 위치 | 규칙 | 위반 시 |
|---|---|---|
| 생성자 | `quantity >= 1` | `INVALID_STOCK_QUANTITY` (400) |
| `decrease(amount)` | `amount >= 1` | `INVALID_INPUT_VALUE` — 음수 차감은 재고를 늘리는 버그 |
| `decrease(amount)` | `amount <= availableQuantity()` | `OUT_OF_STOCK` (409). 검사를 끝낸 뒤에만 값을 바꿔 실패 시 상태 불변 |
| DB | `CONSTRAINT ck_stock_quantity_min CHECK (quantity >= 1)` | 엔티티를 우회한 쓰기(수동 SQL, 향후 벌크 쿼리) 차단 |

- **재고 N이면 판매 가능 수량은 N-1**이다. 의도된 동작이고 `availableQuantity()` / `isSoldOut()`으로
  엔티티가 이 규칙을 소유한다. 메뉴 응답과 차감이 같은 메서드를 본다
- CHECK는 JPA 3.2 표준 `@Table(check = @CheckConstraint(...))`로 선언했다. MySQL은 **8.0.16부터**
  CHECK를 실제로 적용한다(그 전엔 파싱만 하고 무시). 운영 DB는 8.0.45(좌석 경합 세션에서 실측)라 유효하다.
  `ddl-auto: create`라 마이그레이션이 필요 없었다
- 재고 등록/보충 API는 명세에 없어 만들지 않았다

### 결정 2 — 구매는 단일 단계

좌석은 "고른 뒤 결제까지 남이 못 잡게" 점유가 필요했지만 매점 상품은 대체 가능한 수량이라 점유할
대상이 없다. PENDING을 두면 선점 때 재고를 빼고 만료 때 되돌리는 **세션 2의 만료 문제가 그대로
재현**되는데 얻는 것이 없다. 환불도 없으니 `Purchase`에 상태 필드가 필요 없고, 실패한 구매는 기록을
남기지 않는다.

### 결정 3 — 동시성: 비관적 락, 상품 id 순으로 한 건씩

**트랜잭션 경계**는 `PurchaseService.purchase()` 하나다.

```
1. 요청 안 중복 상품 검사, 상품 id 오름차순 정렬
2. 사용자·지점 조회, 운영 상태 확인                    락 없음
3. 상품 일괄 조회 (findAllById)                          락 없음, 가격·이름용
4. 상품마다 순서대로
     SELECT ... FROM stock WHERE branch_id=? AND product_id=? FOR UPDATE   ← 락 획득
     stock.decrease(qty)
5. mock 결제 판정 — 실패면 예외 → 전체 롤백
6. Purchase + items INSERT, stock UPDATE → 커밋 시 락 해제
```

- **락 범위**: 해당 지점 × 요청 상품의 stock 행만. `uk_stock_branch_product`의 동등 조회라 InnoDB는
  레코드 락만 건다. 다른 지점, 같은 지점의 다른 상품은 서로 막지 않는다
- **product는 잠그지 않는다.** 락 쿼리에 `JOIN FETCH s.product`를 넣으면 MySQL `FOR UPDATE`가 조인된
  product 행까지 잠가 **지점이 달라도 같은 상품 구매가 직렬화**된다. 상품은 3단계에서 따로 읽는다.
  실제 SQL에서 `from stock s1_0 where ... for update`로 stock만 잠기는 것을 확인했다
- **데드락 방지**: `IN` 한 방으로 잠그면 획득 순서가 옵티마이저의 인덱스 스캔 순서에 맡겨진다. 정렬 후
  한 건씩 잠가 순서를 코드로 보장했다. 대가는 상품 종류 수만큼의 SELECT인데 한 주문에 한 자릿수다
- **락 보유 시간**: 4단계부터 커밋까지. mock 결제라 즉시 끝난다. 실제 PG였다면 외부 호출 동안 락을 쥐게
  되므로 결제를 트랜잭션 밖으로 빼는 설계가 필요하다. 범위 밖이라 기록만 남긴다
- **락 대기 타임아웃**(`innodb_lock_wait_timeout=3`) → `ConcurrencyFailureException` →
  `STOCK_LOCK_CONFLICT`(409). 좌석의 `SEAT_RESERVATION_CONFLICT`처럼 "품절이 아니라 판정 못 함, 재시도"다
- 좌석에서 비관적 락을 거절한 이유는 **잠글 행이 없어서**였다. 재고는 경합 단위(지점 × 상품)와 정확히
  일치하는 행이 있으므로 비관적 락이 맞다. 인기 상품은 충돌이 잦아 낙관적 락은 재시도가 폭주하고
  `@Version` 컬럼(엔티티 변경)도 필요하다
- 불변식이 엔티티 메서드에 있으므로 조건부 벌크 UPDATE(`quantity - :n >= 1`)는 쓰지 않았다

### 결정 4 — 요청 검증

| 경우 | 응답 | 근거 |
|---|---|---|
| 같은 상품 두 번 | 400 `DUPLICATE_PRODUCT_IN_REQUEST` | 합치지 않고 거부. 장바구니는 상품당 한 줄이라 중복은 클라이언트 버그다. 합치면 요청과 저장 내역이 어긋난다. `DUPLICATE_SEAT_IN_REQUEST`와 같은 판단 |
| 수량 0 이하, 항목 없음, 필수값 누락 | 400 `INVALID_INPUT_VALUE` | Bean Validation(`@Min(1)`, `@NotEmpty`, `@NotNull`) |
| 없는 상품 id | 404 `PRODUCT_NOT_FOUND` | `findAllById` 결과 개수 비교 |
| 상품은 있으나 그 극장 재고 행이 없음 | 409 `OUT_OF_STOCK` | 그 극장에서 팔지 않는 상품. 고객에게는 품절과 같다 |
| 판매 가능 수량 초과 | 409 `OUT_OF_STOCK` | `Stock.decrease()` |
| 휴관·폐관 지점 | 409 `BRANCH_NOT_OPERATING` | 기존 `Branch.isReservable()`(OPEN만 true). 예매 쪽에는 아직 이 검사가 없다 |

### 결정 5 — API

| 메서드 | 경로 | 요청 | 응답 | 에러 |
|---|---|---|---|---|
| GET | `/api/branches/{branchId}/products` | — | 200 `List<StoreMenuResponse>` 상품 id 순 | 404 `BRANCH_NOT_FOUND` |
| POST | `/api/purchases` | body `userId`, `branchId`, `items[{productId, quantity}]`, `paymentResult` | 201 `PurchaseResponse` | 400 `INVALID_INPUT_VALUE` / `DUPLICATE_PRODUCT_IN_REQUEST` · 404 `USER_NOT_FOUND` / `BRANCH_NOT_FOUND` / `PRODUCT_NOT_FOUND` · 409 `BRANCH_NOT_OPERATING` / `OUT_OF_STOCK` / `STOCK_LOCK_CONFLICT` · 402 `PURCHASE_PAYMENT_FAILED` |
| GET | `/api/purchases` | `?userId=` | 200 `List<PurchaseResponse>` 최근 순 | 400 · 404 `USER_NOT_FOUND` |

- **메뉴**: `Stock JOIN FETCH product WHERE branch = ?` 한 방. 지점 확인 포함 **SQL 2건, 상품 수와 무관**.
  원재고는 내리지 않고 판매 가능 수량과 품절 여부만 준다. 휴관 지점도 메뉴는 보여준다(지점 상세와 같은 정책)
- **구매 내역**: DTO 프로젝션(`PurchaseHistoryRow`, 세션 3-2 방식). 엔티티로 fetch join하면
  `branch.description`(TEXT)이 **구매 수 × 항목 수**만큼 반복 전송된다. 항목은 구매마다 1개 이상이라 INNER JOIN.
  `PurchaseResponse.listOf()`가 `LinkedHashMap`으로 묶어 `ORDER BY p.id DESC` 순서를 유지한다
- **구매 응답**은 방금 만든 엔티티에서 바로 만든다. 추가 SELECT 없음
- 컨트롤러는 `StoreMenuController`(`/api/branches/{id}/products`)와 `PurchaseController`로 나눴다.
  메뉴 경로가 branches 아래지만 재고를 읽으므로 store 도메인에 둔다

### 결정 6 — mock 결제는 예매의 `PaymentResult`를 재사용

- 예매는 결제가 별도 호출이라 enum을 `PaymentRequest`로 받았다. 매점은 단일 단계라 구매 요청 본문에
  `paymentResult`로 받는다. enum은 `reservation.dto.PaymentRequest.PaymentResult`를 그대로 import한다
  (dto 참조는 도메인 규칙상 허용, service만 금지)
- **재고를 확보한 뒤 결제를 판정한다.** 결제부터 하면 돈은 나갔는데 품절인 경우가 생긴다
- FAILURE → `PURCHASE_PAYMENT_FAILED`(402) → **전체 롤백**. 예매의 `noRollbackFor`와 반대다. 예매는 실패해도
  좌석 해제를 남겨야 했지만 매점은 남길 것이 없다
- 기존 `PAYMENT_FAILED`는 문구가 "좌석 선택부터 다시"라 예매 전용이다. 문구를 바꾸면 예매 응답이 바뀌므로
  새 코드를 뒀다

### ErrorCode 추가

`INVALID_STOCK_QUANTITY`(400), `DUPLICATE_PRODUCT_IN_REQUEST`(400), `BRANCH_NOT_OPERATING`(409),
`STOCK_LOCK_CONFLICT`(409), `PURCHASE_PAYMENT_FAILED`(402). `OUT_OF_STOCK`, `PRODUCT_NOT_FOUND`는 기존 것.

### 회귀 테스트

| 클래스 | 수 | 내용 |
|---|---|---|
| `StockTest` | 5 | 생성 시 1 미만 거부, 1개 남기는 차감, 1 미만 만드는 차감 거부 + 값 불변, 음수 차감 거부, N-1 |
| `StoreMenuControllerTest` | 4 | 판매 가능 수량·품절, 극장별 분리, 404, **상품 3개에서 SQL 2건**(Statistics, N+1 회귀) |
| `PurchaseControllerTest` | 13 | 성공 201 + 차감 + unitPrice·총액, 1개 남기는 구매, 초과 409, 중복 400, 수량 0 400, 빈 항목 400, 없는 상품 404, 재고 행 없음 409, 휴관 409, 없는 사용자 404, 결제 실패 402, 내역 최근 순, 내역 404 |
| `PurchaseRollbackTest` | 3 | 부분 성공 없음(A 차감 후 B 실패 → A 원복), 결제 실패 시 재고 원복·기록 없음, **DB CHECK가 우회 쓰기를 막음** |
| `PurchaseConcurrencyTest` | 2 | 재고 11(판매 가능 10)에 20스레드 동시 구매 → 성공 정확히 10, 나머지 전부 `OUT_OF_STOCK`, 최종 재고 1 / [A,B]·[B,A] 엇갈린 10스레드 → 전원 성공 |

**롤백 검증은 실제 트랜잭션에서 한다.** `@Transactional` 테스트 안에서는 서비스가 바깥 테스트 트랜잭션에
참여하므로 롤백이 테스트 끝까지 미뤄지고, 차감된 값이 영속성 컨텍스트에 남아 "원복됐다"를 확인할 수 없다.
그래서 부분 성공·결제 실패는 `ReservationConcurrencyTest`처럼 비트랜잭션 + `@AfterEach` 정리로 분리했다.

CHECK 검증 테스트는 `EntityManager`를 직접 써서 Spring 예외 번역을 거치지 않는다. 기대 예외는
`DataIntegrityViolationException`이 아니라 Hibernate `ConstraintViolationException`이고, 메시지에 제약 이름이
실리는 것까지 확인한다.

동시성 테스트는 3회 반복 실행해 모두 통과했다. 테스트 DB는 H2라 InnoDB의 레코드 락 범위까지 재현하지는
않는다. 검증하는 것은 lost update가 없는지와 획득 순서가 고정되는지다.

### 검토했으나 하지 않은 것

- `Purchase.purchasedAt`을 `Clock` 주입으로 변경 — 시각 기반 로직이 없어 테스트 이득 없이 엔티티만 바뀐다
- 구매 단건 조회, 내역 페이지네이션 — 요청 범위 밖
- `IN` 한 방 잠금 — 획득 순서를 코드로 보장할 수 없다
- 조건부 벌크 UPDATE — 불변식을 엔티티 밖으로 빼게 된다

### 확인

`./gradlew test` 143개 통과(기존 116 + 신규 27). 생성 DDL에
`constraint ck_stock_quantity_min check (quantity >= 1)`이 들어가는 것을 확인했다.

---

## 3주차 세션 1: 회원가입 / 로그인 / Access Token 발급·검증

3주차 JWT 인증의 첫 세션. 사용자 생성 → 비밀번호 인증 → 토큰 발급·검증까지만 만들었다.
토큰을 요청에서 꺼내 SecurityContext에 넣는 필터와 보호 경로 구분은 세션 2에서 한다.
그래서 이번 검증 로직의 목표는 "다음 필터가 만료 / 변조 / 형식 오류를 서로 다른 코드로 응답할 수 있는 구조"다.

### 버전 확인

| 항목 | 버전 | 확인 방법 |
|---|---|---|
| Spring Boot | 4.1.1 | `build.gradle` |
| Spring Security | 7.1.1 | Boot BOM |
| JJWT | 0.13.0 | Maven Central 최신 안정판 |

- JJWT는 0.12 이후 API만 쓴다(`Jwts.parser().verifyWith().build().parseSignedClaims()`, `Jwts.builder().subject()`).
  `parserBuilder()`, `setSigningKey()`, `setSubject()` 계열은 쓰지 않는다
- JJWT에 Jackson 3 모듈이 없어 `jjwt-jackson`(Jackson 2)을 썼다. Boot 4 BOM이 Jackson 2(2.21.5)도 관리한다
- Security 7의 `DaoAuthenticationProvider`는 `UserDetailsService`를 **생성자로만** 받는다(`setUserDetailsService` 제거).
  `setPasswordEncoder`는 jar에서 deprecated가 아님을 `javap`로 확인했다

### 결정 1 — AuthenticationManager는 직접 조립한다

```java
DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
provider.setPasswordEncoder(passwordEncoder);
return new ProviderManager(provider);
```

`AuthenticationConfiguration.getAuthenticationManager()`도 같은 조립을 하지만, Spring이 빈을 찾아 연결하므로
"어떤 Provider가 어떤 UserDetailsService·PasswordEncoder로 비교하는지"가 코드에 보이지 않는다.

로그인 호출 순서:

```
AuthService.login()                                     ← 우리 코드
 └ authenticationManager.authenticate(unauthenticated token)
    └ ProviderManager → DaoAuthenticationProvider       ← Spring
       ├ LoginUserDetailsService.loadUserByUsername()   ← 우리 구현, Spring이 호출
       ├ passwordEncoder.matches(raw, hash)             ← Spring이 호출
       └ 성공 → authenticated token, eraseCredentials()
 └ jwtProvider.createAccessToken()                      ← 우리 코드
```

### 결정 2 — 로그인 실패는 단일 응답

- `DaoAuthenticationProvider`는 `hideUserNotFoundExceptions=true`(기본)라 `UsernameNotFoundException`을
  `BadCredentialsException`으로 바꾼다. 계정이 없을 때도 더미 해시로 `matches()`를 돌려(`mitigateAgainstTimingAttack`)
  응답 시간 차이를 줄인다
- `AuthService.login()`에서 `BadCredentialsException`만 `LOGIN_FAILED`(401)로 바꾼다
- `AuthenticationException` 전체를 잡지 않는다. `InternalAuthenticationServiceException`(DB 장애 등)이
  "비밀번호 틀림"으로 가려지면 안 된다
- 로그인 요청에는 가입 형식 규칙을 적용하지 않고 `@NotBlank`만 둔다. 400과 401이 갈리면 규칙·존재 여부를 추측할 단서가 된다

두 응답을 다르게 주면 아이디 목록을 대입해 가입된 계정만 추려내는(계정 열거) 공격이 가능해지고,
추려낸 계정에 비밀번호 대입을 집중할 수 있다.

### 결정 3 — UserDetails는 둘로 나눈다

| 타입 | 쓰이는 곳 | 비밀번호 |
|---|---|---|
| `LoginUserDetails` (`UserDetails`, `CredentialsContainer`) | 로그인 한 번 | 해시 보유, 인증 후 `ProviderManager`가 지움 |
| `AuthUser(userId, role)` record | JWT 검증 결과, 세션 2 필터의 principal | 필드 자체가 없음 |

하나로 합치면 JWT 경로에서 `getPassword()`에 쓰면 안 되는 빈 값을 채워야 한다.
`LoginUserDetails`가 userId를 들고 있어 인증 직후 재조회 없이 `sub`를 만든다.

**구현 중 걸린 것**: Security 7은 인증 성공 시 권한에 `FACTOR_PASSWORD`를 덧붙인다(다중 인증 지원).
토큰에는 `role`만 싣기 때문에 이 권한은 로그인 요청 밖으로 나가지 않는다. 테스트에서 이 동작을 명시했다.

### 결정 4 — 토큰 검증 결과는 `AuthUser` 또는 유형별 `CustomException`

| 실제 발생한 JJWT 예외 | 상황 | ErrorCode |
|---|---|---|
| `ExpiredJwtException` | 만료 | `EXPIRED_TOKEN` |
| `security.SignatureException` | payload 변조, 다른 키, HS512 | `INVALID_TOKEN` |
| `UnsupportedJwtException` | `alg: none` | `INVALID_TOKEN` |
| `IncorrectClaimException` | 다른 `iss` | `INVALID_TOKEN` |
| `MalformedJwtException` | 조각 수·Base64·JSON 오류 | `MALFORMED_TOKEN` |
| `IllegalArgumentException` | null, 빈 문자열, 공백 | `MALFORMED_TOKEN` |

- 세션 2 필터는 `catch (CustomException e)` → `e.getErrorCode()`로 응답을 고른다. 필터는
  `@RestControllerAdvice` 밖이라 어차피 직접 잡아야 한다
- JJWT는 서명을 먼저 검증하고 그다음 exp·iss를 본다. **만료 + 변조 토큰은 `INVALID_TOKEN`** 이 나온다.
  "만료" 판정은 우리가 발급한 게 확실한 토큰에만 붙는다
- 허용 알고리즘을 `sig().clear().add(HS256)`로 못박았다. 헤더의 `alg`는 토큰을 만든 쪽이 정하는 값이다
- enum 결과(`TokenStatus`) 반환은 호출자가 VALID 확인을 잊으면 실패 토큰으로 인증이 되므로 택하지 않았다

토큰 구성: `sub`=userId 문자열, `role`=USER|ADMIN(접두사 없음), `iat`, `exp`, `iss`=`cgv-api`(코드 상수). `aud`는 생략.
HS256, 키는 Base64 디코딩 후 256비트 이상이어야 하고, 미만이면 `WeakKeyException`으로 기동이 실패한다.
발급·검증 시각은 기존 `Clock` 빈을 쓴다.

설정은 `jwt.secret: ${JWT_SECRET}`, `jwt.access-token-validity: ${JWT_ACCESS_TOKEN_VALIDITY}`이고 기본값이 없다.
실제 값은 Git에 올리지 않는 `.env`로 주입한다. 테스트 yaml에는 테스트 전용 더미 키를 뒀다.

### 결정 5 — 회원가입 검증

| 필드 | 규칙 | 이유 |
|---|---|---|
| loginId | `^[a-z0-9]{4,20}$` | MySQL 기본 collation이 대소문자를 구분하지 않아 `Abc`/`abc`가 unique에서 충돌한다 |
| password | 공백 없는 ASCII 8~64자 | BCrypt는 72바이트 초과를 거부한다. 한글은 글자당 3바이트라 길이 제한만으로는 못 막는다 |
| name | `@NotBlank @Size(max=50)` | 컬럼 길이 |
| birthDate | `@NotNull @Past` | ISO `yyyy-MM-dd` |
| email | `@NotBlank @Email @Size(max=100)` | unique 없음. 로그인에 쓰지 않는다 |
| phoneNumber | `^01[016789][0-9]{7,8}$` | **하이픈 없이 숫자만** 저장·수신. 표시 형식은 클라이언트 몫 |

- loginId 중복: `existsByLoginId` pre-check + `saveAndFlush`의 `DataIntegrityViolationException` → 409 `DUPLICATE_LOGIN_ID`.
  users의 unique 제약은 `login_id` 하나라 오역 여지가 없다
- `HttpMessageNotReadableException`이 처리되지 않아 `"2000-13-01"` 같은 본문이 **500**으로 나가던 것을 발견해
  400 `INVALID_INPUT_VALUE`로 매핑했다. 전역 핸들러라 다른 API의 JSON 오류에도 적용된다

### 결정 6 — role은 두 겹으로 막는다

1. `SignupRequest`에 `role` 필드가 없다. Jackson이 모르는 필드를 버린다
2. `User` 빌더에 role 파라미터가 없고 생성자가 `Role.USER`로 고정한다

관리자 계정 생성은 세션 3에서 의도가 드러나는 별도 메서드로 추가한다(엔티티 변경이라 그때 승인받는다).
`ROLE_` 접두사는 `Role.getAuthority()` 한 곳에서만 붙인다.

### 엔티티 변경

`User`에 `email`(varchar 100), `phoneNumber`(varchar 11), `role`(enum, not null) 추가. unique는 `login_id`만.
`Role` enum 신설. 기존 `User.builder()` 사용처는 `TestFixtures.user()`, `ReservationServiceTest.userWithId()` 두 곳이었다.

### 패키지 신설 — `global/security`

`JwtProvider`, `JwtProperties`, `AuthUser`, `LoginUserDetails`, `LoginUserDetailsService`.
Spring Security 어댑터 모음이라 도메인 밖에 뒀다. CLAUDE.md 패키지 트리에 없는 새 패키지다.
회원가입·로그인 API는 `domain/user`(`AuthController`, `AuthService`)에 있다.

### 최소 SecurityConfig (임시)

STATELESS, csrf/formLogin/httpBasic/logout 비활성화, `POST /api/auth/signup`·`/api/auth/login` permitAll,
**나머지도 임시 permitAll**(주석 표시). 세션 2에서 `authenticated()`로 바꾼다.

CSRF를 끈 근거는 "인증 수단이 `Authorization` 헤더뿐"이라는 전제다. 쿠키 인증으로 바꾸면 다시 켜야 한다.

`ControllerIntegrationTest`의 MockMvc는 `springSecurity()`를 적용하지 않아 필터 체인을 타지 않는다.
그래서 `SecurityConfigTest`를 따로 두고 필터를 태운 상태로 기존 API 개방, CSRF 없는 POST, 세션 미생성을 확인했다.

### API

| 메서드 | 경로 | 요청 | 응답 | 에러 |
|---|---|---|---|---|
| POST | `/api/auth/signup` | `{loginId, password, name, birthDate, email, phoneNumber}` | 201 `{userId, loginId}` | 400 `INVALID_INPUT_VALUE` · 409 `DUPLICATE_LOGIN_ID` |
| POST | `/api/auth/login` | `{loginId, password}` | 200 `{accessToken, tokenType, expiresIn}` | 400 `INVALID_INPUT_VALUE` · 401 `LOGIN_FAILED` |

### ErrorCode 추가

`DUPLICATE_LOGIN_ID`(409), `LOGIN_FAILED`(401), `EXPIRED_TOKEN`(401), `INVALID_TOKEN`(401), `MALFORMED_TOKEN`(401).
인증 없음·권한 없음 코드는 세션 2에서 추가한다.

### 회귀 테스트

| 클래스 | 수 | 내용 |
|---|---|---|
| `SecurityConfigTest` | 3 | 필터를 태운 상태로 기존 GET 200, CSRF 없는 POST 성공, 세션·쿠키 미생성 |
| `AuthControllerTest` | 22 | 해시 저장 + USER 생성, **본문 `role: ADMIN` 무시**, 같은 비밀번호도 해시 다름, 중복 409, 형식 오류 400(11종), 필수값 누락, 날짜 파싱 실패 400, 로그인 토큰의 sub·role, 가입 후 로그인, **비밀번호 틀림과 계정 없음의 응답 본문 완전 동일**, 빈 값 400, 인증 후 해시 삭제 |
| `JwtProviderTest` | 17 | 왕복, claim이 정확히 5개, 만료·만료 직전, payload 변조, 다른 키, alg=none, HS512, 다른 iss, 만료+변조 → INVALID, 형식 오류 6종, 짧은 키 기동 실패 |

JJWT 예외 분류는 임시 테스트로 입력마다 실제 발생 예외를 찍어 확인한 뒤 지웠다(결정 4의 표).

### 확인

`./gradlew test` 185개 통과(기존 143 + 신규 42). 로컬 실행 시 `.env`에 `JWT_SECRET`, `JWT_ACCESS_TOKEN_VALIDITY`가 필요하다.
`UserDetailsService` 빈이 생기면서 Boot의 `Using generated security password` 로그가 사라졌다.
