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
