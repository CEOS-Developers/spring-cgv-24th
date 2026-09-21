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
| `TheaterType` | STANDARD(8×10) / SPECIAL(10×20) | `getTotalSeatCount()`, `isValidSeat()` 보유 |
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
