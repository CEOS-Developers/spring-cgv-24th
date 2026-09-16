## 🎬 CGV 클론코딩 서비스 소개

CGV의 핵심 서비스를 클론코딩한 프로젝트입니다. 영화관 조회/찜, 영화 조회/찜, 영화 예매 및 취소, 매점 구매 6가지 핵심 기능을 구현했으며, 실제 CGV 서비스 화면을 참고하여 DB 모델링을 진행했습니다.

## ERD

![img_1.png](img_1.png)
📎 [ERDCloud에서 보기](https://www.erdcloud.com/d/W8KHbPARz5j4dP2Ay)

## 1. 전체 엔티티 연관관계 정리

### 1-1. 유저

| 엔티티 | 설명 |
|---|---|
| `User` | 로그인 아이디(`loginId`) 기반 회원 정보. `createdAt`은 `BaseTimeEntity` 상속으로 자동 관리 |

### 1-2. 영화관 / 상영관 / 좌석

| 관계 | 타입 | 설명 | FK |
|---|---|---|---|
| Theater - Screen | 1 : N | 하나의 영화관은 여러 상영관을 가진다 | `Screen.theaterId` |
| ScreenType - Screen | 1 : N | 하나의 상영관 타입(일반관/IMAX 등)은 여러 상영관에 적용될 수 있다 | `Screen.screenTypeId` |
| Screen - Seat | 1 : N | 하나의 상영관은 여러 좌석을 가진다 | `Seat.screenId` |
| Theater - TheaterLike | 1 : N | 하나의 영화관은 여러 사용자에게 찜될 수 있다 (User와 TheaterLike를 통한 N:M) | `TheaterLike.theaterId` |

**설계 포인트 —**
"특별관/일반관 종류가 같으면 좌석이 동일하다"는 요구사항 때문에 처음엔 Seat를 ScreenType에 직접 연결하는 방식을 고려했으나, 이 경우 물리적으로 다른 상영관(예: 강남 IMAX 1관, 용산 IMAX 2관)이 동일한 Seat row를 공유하게 되어 좌석 점유 상태를 정확히 표현할 수 없는 문제가 있었습니다. 따라서 **Seat는 Screen에 직접 종속**시키고, "같은 타입이면 좌석 배치가 동일하다"는 요구사항은 **좌석 생성 시점의 로직**(ScreenType의 `totalRow × totalCol` 값을 참조해 생성)으로 보장하는 방식을 택했습니다.

### 1-3. 영화

| 관계 | 타입 | 설명 | FK |
|---|---|---|---|
| Movie - MovieImage | 1 : N | 하나의 영화는 여러 장의 포스터/스틸컷 이미지를 가진다 (`type`으로 POSTER/STILL 구분) | `MovieImage.movieId` |
| Movie - MovieStatistics | 1 : 1 | 하나의 영화는 하나의 통계 정보(예매율, 누적관객수, 에그지수)를 가진다 | `MovieStatistics.movieId` |
| Movie - MoviePerson | 1 : N | 하나의 영화에는 여러 감독/배우 정보가 연결될 수 있다 | `MoviePerson.movieId` |
| Person - MoviePerson | 1 : N | 한 인물은 여러 영화에 연결될 수 있다 (Movie와 Person이 MoviePerson을 통한 N:M) | `MoviePerson.personId` |
| Movie - MovieLike | 1 : N | 하나의 영화는 여러 사용자에게 찜될 수 있다 (User와 MovieLike를 통한 N:M) | `MovieLike.movieId` |
| Movie - Review | 1 : N | 하나의 영화에는 여러 리뷰가 작성될 수 있다 | `Review.movieId` |
| User - Review | 1 : N | 하나의 유저는 여러 영화에 리뷰를 작성할 수 있다 | `Review.userId` |

**설계 포인트 —**
실제 CGV 서비스를 참고해 상세페이지 구성 요소(포스터/스틸컷, 감독/배우, 예매율·누적관객수·에그지수, 실관람평)를 모두 반영하여 모델링을 진행했습니다. 다만 **API 구현은 미션에 명시된 6가지 기능 범위로 한정**했고, Review/Person은 Domain·Repository 계층까지만 구현했습니다.

**MovieStatistics를 Movie와 분리한 이유:**
예매율, 누적관객수, 에그지수 등은 정적인 영화 정보(제목, 줄거리 등)와 달리 계속 갱신되는 집계성 데이터라는 점에서 성격이 다르다고 판단해 별도 테이블로 분리했습니다. 등록/수정 API는 PATCH 하나로 upsert(있으면 갱신, 없으면 생성) 방식으로 구현했습니다.

### 1-4. 상영/예매

| 관계 | 타입 | 설명 | FK |
|---|---|---|---|
| Movie - Schedule | 1 : N | 하나의 영화는 여러 상영 회차를 가질 수 있다 | `Schedule.movieId` |
| Screen - Schedule | 1 : N | 하나의 상영관에서는 여러 상영 회차가 존재할 수 있다 | `Schedule.screenId` |
| User - Reservation | 1 : N | 하나의 유저는 여러 예매를 할 수 있다 | `Reservation.userId` |
| Schedule - Reservation | 1 : N | 하나의 상영 회차에는 여러 예매가 발생할 수 있다 | `Reservation.scheduleId` |
| Reservation - ReservationSeat | 1 : N | 하나의 예매는 여러 좌석을 포함할 수 있다 | `ReservationSeat.reservationId` |
| Seat - ReservationSeat | 1 : N | 하나의 좌석은 여러 회차에서 반복적으로 예매될 수 있다 | `ReservationSeat.seatId` |
| Schedule - ReservationSeat | 1 : N | 하나의 상영 회차에는 여러 예약 좌석 정보가 존재할 수 있다 | `ReservationSeat.scheduleId` |

**ReservationSeat에 scheduleId를 중복 저장한 이유**

`ReservationSeat`에는 이미 `reservationId`가 있고, `Reservation`에도 `scheduleId`가 있어 언뜻 중복처럼 보이지만, 이는 의도적인 비정규화입니다.

1. **동일 상영 회차의 동일 좌석 중복 예매 방지**: "같은 상영 회차(scheduleId)에서 같은 좌석(seatId)은 한 번만 예약 가능해야 한다"는 무결성을 DB 레벨에서 `UNIQUE(schedule_id, seat_id)` 제약으로 강제하려면, `ReservationSeat` 테이블 자체가 두 컬럼을 모두 갖고 있어야 합니다. `Reservation`을 조인해야만 회차를 알 수 있다면 단일 테이블 기준 유니크 제약을 만들 수 없습니다.
2. **팩토리 메서드로 불일치 가능성 차단**: `ReservationSeat.create(reservation, seat)`는 파라미터로 schedule을 별도로 받지 않고, 내부에서 `reservation.getSchedule()`을 그대로 사용하도록 구현하여 "예매의 상영 회차와 예매좌석의 상영 회차가 다르게 저장되는" 실수를 원천 차단했습니다.

**취소 시 ReservationSeat를 삭제하는 이유**
좌석 점유 여부는 `ReservationSeat`의 존재 여부로 판단하므로, 예매가 취소되면 해당 좌석이 다시 예매 가능한 상태로 돌아가야 합니다. 따라서 `Reservation.status`는 `CANCELLED`로 남기되(예매 이력 보존), `ReservationSeat` row는 삭제하여 좌석 재예매를 가능하게 했습니다. 다만 이 경우 취소된 예매를 조회했을 때 "어떤 좌석을 예매했었는지"를 알 수 없게 되는 문제가 있어, `Reservation`에 `seatSummary`(예: "A1, A2") 필드를 두어 예매 시점의 좌석 정보를 스냅샷으로 별도 보존했습니다.

**예매 취소 시간 제약**
실제 CGV 정책(상영 시작 20분 전까지 온라인 취소 가능)을 참고하여, 예매는 상영 시작 전까지, 취소는 상영 시작 20분 전까지만 가능하도록 서비스 로직에서 검증합니다.

### 1-5. 매점

| 관계 | 타입 | 설명 | FK |
|---|---|---|---|
| Item - Stock | 1 : N | 하나의 매점 메뉴는 여러 영화관에서 재고로 관리된다 | `Stock.itemId` |
| Theater - Stock | 1 : N | 하나의 영화관은 여러 매점 상품의 재고를 가진다 (Item과 Theater가 Stock을 통한 N:M) | `Stock.theaterId` |
| User - Order | 1 : N | 하나의 유저는 여러 매점 주문을 할 수 있다 | `Order.userId` |
| Theater - Order | 1 : N | 하나의 영화관에서는 여러 매점 주문이 발생할 수 있다 | `Order.theaterId` |
| Order - OrderItem | 1 : N | 하나의 주문은 여러 상품 항목을 포함할 수 있다 | `OrderItem.orderId` |
| Item - OrderItem | 1 : N | 하나의 상품은 여러 주문 항목에 포함될 수 있다 | `OrderItem.itemId` |

**설계 포인트 — Item(공통 메뉴)과 Stock(지점별 재고) 분리**
"모든 영화관의 매점 메뉴는 같지만, 재고는 지점별로 다르다"는 요구사항을 반영해 상품 마스터(`Item`)와 지점별 재고(`Stock`)를 분리했습니다.

**재고 차감의 동시성 처리**
재고 확인 후 차감하는 방식(select-then-update)은 동시 주문 시 재고가 음수가 될 수 있는 문제가 있어, `UPDATE ... SET quantity = quantity - ? WHERE quantity >= ?` 형태의 **조건부 UPDATE 쿼리**로 재고 차감을 원자적으로 처리했습니다. 영향받은 row가 0개면 재고 부족으로 판단해 예외를 던지고 트랜잭션 전체를 롤백합니다.

**가격 스냅샷**
`OrderItem.price`에는 주문 시점의 `Item.price`를 그대로 복사해 저장합니다. 이는 이후 매점 가격이 변동되더라도 과거 주문 내역의 결제 금액이 바뀌지 않도록 하기 위함입니다.

**"재고는 항상 1 이상" 요구사항 해석**

이 문구를 "재고 등록 시점"과 "구매로 인한 소진 시점" 두 가지로 나누어 해석했습니다.

- **등록 시점**: `Stock` 생성 시 `@Min(1)` 검증을 걸어, 재고를 0이나 음수로 초기 등록하는 것은 허용하지 않습니다.
- **구매(소진) 시점**: 정상적인 구매 누적으로 재고가 0이 되는 것은 "품절" 상태로 간주해 허용합니다. "재고는 항상 1 이상"을 "구매 후에도 항상 최소 1개가 남아있어야 한다"는 의미로 해석하면 마지막 남은 재고를 영원히 판매할 수 없는 매점이 되어 버리기 때문에, 이 문구는 데이터 정합성 관점(재고가 음수로 관리되거나, 처음부터 0으로 등록되는 경우가 없다는 것)의 규칙으로 해석했습니다.
- 재고 차감은 `UPDATE ... SET quantity = quantity - ? WHERE quantity >= ?` 형태의 조건부 UPDATE로 처리하여, 요청 수량만큼 남아있을 때만 차감이 성공하고 0까지는 정상적으로 소진될 수 있습니다.

## 2. 유니크 제약 조건 정리

| 테이블 | 제약 | 목적 |
|---|---|---|
| ReservationSeat | `UNIQUE(schedule_id, seat_id)` | 동일 회차에서 동일 좌석 중복 예매 방지 |
| Seat | `UNIQUE(screen_id, row_num, col_num)` | 같은 상영관 안에 동일 좌표(예: A1)의 좌석이 중복 생성되는 것 방지 |
| MovieLike | `UNIQUE(user_id, movie_id)` | 한 사용자가 같은 영화를 여러 번 찜하는 것 방지 |
| TheaterLike | `UNIQUE(user_id, theater_id)` | 한 사용자가 같은 영화관을 여러 번 찜하는 것 방지 |
| Stock | `UNIQUE(theater_id, item_id)` | 한 영화관에 같은 상품의 재고 row가 중복 생성되는 것 방지 |
| MovieStatistics | `UNIQUE(movie_id)` | 하나의 영화에 하나의 통계만 존재하도록 보장 (1:1 관계) |
| User | `UNIQUE(login_id)` | 로그인 아이디 중복 가입 방지 |

## 3. 서비스 로직에서 추가로 검증하는 부분

DB 제약만으로는 표현할 수 없어 서비스 계층에서 별도로 검증한 항목입니다.

- **좌석-상영관 일치 검증**: 예매 요청의 좌석들이 해당 상영 회차(`Schedule`)가 속한 상영관(`Screen`)의 좌석이 맞는지 확인
- **예매/취소 가능 시간 검증**: 상영 시작 전(예매), 상영 시작 20분 전(취소)까지만 허용
- **동시 예매 방어(2단계)**: 애플리케이션 레벨에서 좌석 점유 여부를 우선 조회해 명확한 에러 메시지(`SEAT_ALREADY_RESERVED`)를 반환하고, 동시 요청이 이 검증을 함께 통과하는 극단적 케이스는 DB 유니크 제약 위반(`DataIntegrityViolationException`)을 전역 예외 처리기에서 409로 변환해 최종 방어
- **재고 검증 및 원자적 차감**: 조건부 UPDATE 쿼리로 재고 부족 상황을 동시성 이슈 없이 처리
- **동일 상품 중복 주문 라인 병합**: 하나의 주문 요청에 같은 상품이 여러 줄로 들어와도 수량을 합산해 하나의 `OrderItem`으로 저장
- **본인 소유 데이터 검증**: 예매 상세조회/취소, 주문 상세조회는 요청한 사용자가 실제 소유자인지 확인 (`IsOwnedBy` 형태의 도메인 메서드로 구현)

## 4. 타임스탬프 설계 원칙

- **사건이 발생한 절대 시점**(User.createdAt, Reservation.reservedAt, Order.orderedAt 등) → `Instant` 사용
- **사람이 인지하는 로컬 시각**(Schedule.startTime/endTime — "9/14 19:30 상영") → `LocalDateTime` 사용
- **날짜만 의미 있는 값**(Movie.openDate/closeDate) → `LocalDate` 사용
