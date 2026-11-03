# API 간단 명세서

현재 Controller와 SecurityConfig를 기준으로 작성했습니다. 결제 데이터의 저장 기준은 [결제 조회 설계](payment-history.md)를 참고하세요.

## 공통

- `payment-test` 프로필 기본 주소: `http://localhost:8081`
- JSON 요청: `Content-Type: application/json`
- **로그인**: `Authorization: Bearer <accessToken>` 필요. 사용자 ID는 JWT에서 가져옵니다.
- **관리자**: 로그인 + `ADMIN` 권한 필요. **공개**: 토큰 없이 요청할 수 있습니다.
- 아래 경로의 `{...}`는 실제 ID로 바꿉니다. 요청 항목은 별도 표시가 없으면 JSON Body입니다.
- 도메인 오류 응답: `{"status":404,"code":"PAYMENT_NOT_FOUND","message":"..."}`

## 인증·관리자

| Method | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/auth/signup` | 공개 | `name, email, password, phone` | 201 회원 정보 |
| POST | `/api/auth/login` | 공개 | `email, password` | 200 `accessToken` |
| GET | `/api/admin/check` | 관리자 | 없음 | 200 접근 확인 문자열 |
| POST | `/api/admin/signup` | 관리자 | 회원가입과 동일 | 201 관리자 회원 정보 |

비밀번호는 8~20자입니다. 관리자 가입 API는 기존 관리자 인증이 필요합니다.

## 영화·영화관·상영회차

| Method | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/movies` | 관리자 | `titleKr, titleEn, runtimeMinutes, ageRating, releaseDate, director, castMembers` | 201 영화 |
| GET | `/api/movies` | 공개 | 없음 | 200 영화 목록 |
| GET | `/api/movies/{movieId}` | 공개 | 없음 | 200 영화 |
| GET | `/api/movies/{movieId}/screenings` | 공개 | 없음 | 200 상영회차 목록 |
| POST | `/api/theaters` | 관리자 | `theaterName, region` | 201 영화관 |
| GET | `/api/theaters` | 공개 | 선택 Query: `region` | 200 영화관 목록 |
| GET | `/api/theaters/{theaterId}` | 공개 | 없음 | 200 영화관 |
| GET | `/api/theaters/{theaterId}/screenings` | 공개 | 없음 | 200 상영회차 목록 |
| POST | `/api/screenings` | 관리자 | `screenId, movieId, screeningDate, startTime, endTime, screeningType` | 201 상영회차 |

날짜는 `yyyy-MM-dd`, 시간은 `HH:mm:ss`입니다. `region`은 `Seoul / Gyeonggi / Incheon / Gangwon / Chungcheong / Daegu / Busan` 중 하나입니다.
상영회차 등록 시 해당 상영관 좌석의 `ScreeningSeat`도 생성합니다. 상영관·좌석·요금·매점 메뉴를 생성하는 별도 공개 API는 현재 없습니다. 테스트 초기 데이터가 필요합니다.

## 예매

| Method | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/reservations` | 로그인 | `screeningId, childCount, youthCount, adultCount, seniorCount, seatIds` | 201 본인 대기 예매 |
| GET | `/api/reservations` | 로그인 | 없음 | 200 본인 예매 목록 |
| GET | `/api/reservations/{reservationId}` | 로그인 | 없음 | 200 본인 예매 |
| PATCH | `/api/reservations/{reservationId}/cancel` | 로그인 | 없음 | 200 취소 예매 |

생성 시 좌석을 10분간 점유하고 DB 요금으로 금액을 계산합니다. `Pending → Reserved` 확정은 서버 결제 승인 성공 시 처리합니다. 결제 처리 중이거나 이미 결제 완료된 예매는 이 취소 API로 취소할 수 없습니다.

```json
{
  "screeningId": 1,
  "childCount": 0,
  "youthCount": 0,
  "adultCount": 2,
  "seniorCount": 0,
  "seatIds": [1, 2]
}
```

## 매점

| Method | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/shop/{theaterId}/inventories` | 관리자 | `menuId, stock` | 201 재고 |
| GET | `/api/shop/{theaterId}/inventories` | 로그인 | 없음 | 200 재고 목록 |
| PATCH | `/api/shop/{theaterId}/inventories/{inventoryId}/addStock` | 관리자 | Query: `addQuantity` | 200 변경 재고 |
| POST | `/api/shop/orders` | 로그인 | `theaterId, items: [{menuId, quantity}]` | 201 본인 대기 주문 |
| GET | `/api/shop/orders/{orderId}` | 로그인 | 없음 | 200 본인 주문 |
| PATCH | `/api/shop/orders/{orderId}/cancel` | 로그인 | 없음 | 200 취소 주문 |

주문 생성 시 재고를 10분간 확보하고 주문 시점 단가를 저장합니다. 미결제 주문 취소·만료 시 재고를 복구합니다. 결제 처리 중·결제 완료 주문은 이 취소 API로 취소할 수 없습니다.

## 결제

| Method | 경로 | 권한 | 요청 | 성공 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/payments/reservations/prepare` | 로그인 | `reservationId` | 200 결제 준비 정보 |
| POST | `/api/payments/store-orders/prepare` | 로그인 | `storeOrderId` | 200 결제 준비 정보 |
| POST | `/api/payments/confirm` | 로그인 | `paymentKey, orderId, amount` | 200 결제 승인 결과 |
| GET | `/api/payments` | 로그인 | 선택 Query: `page=0, size=20` | 200 본인 완료 결제 목록 |
| GET | `/api/payments/{paymentId}` | 로그인 | 없음 | 200 본인 완료 결제 상세 |

### 진행 순서

1. 예매 또는 매점 주문 생성 API로 대기 데이터를 만듭니다.
2. 해당 ID로 결제를 준비합니다. 서버가 DB 금액을 사용하고 유효한 기존 `READY` 결제가 있으면 재사용합니다.
3. 프런트엔드에서 준비 응답의 `orderId, orderName, amount`로 Toss 결제를 요청합니다.
4. Toss 성공 리다이렉트의 `paymentKey, orderId, amount`를 서버 승인 API에 보냅니다. 리다이렉트만으로 결제 완료 처리하지 않습니다.
5. 서버가 Toss 결과를 검증하고 결제 `DONE`과 예매 `Reserved` 또는 매점 주문 `PAID`를 같은 트랜잭션에서 저장합니다.
6. 승인 응답의 **`paymentId`**로 완료 결제를 조회합니다.

`paymentId`는 우리 결제 PK, `pay_...` 형태의 `orderId`는 Toss 연동 주문번호입니다. 매점 주문의 숫자 `orderId`와도 구분해야 합니다.

준비 응답 예시:

```json
{
  "paymentId": 2,
  "orderId": "pay_12345678-1234-1234-1234-123456789abc",
  "orderName": "영화 티켓 예매",
  "amount": 28000,
  "status": "READY",
  "expiresAt": "2026-11-03T21:10:00"
}
```

서버 승인 요청 예시:

```json
{
  "paymentKey": "Toss가 반환한 paymentKey",
  "orderId": "pay_12345678-1234-1234-1234-123456789abc",
  "amount": 28000
}
```

서버 승인 응답은 `paymentId, orderId, orderName, amount, status, approvedAt`와 구매 종류에 따라 `reservationId, reservationStatus` 또는 `storeOrderId, storeOrderStatus`를 포함합니다. 완료된 동일 승인 요청은 저장된 결과를 반환합니다. 승인 결과가 불명확하면 `503 PAYMENT_RESULT_UNKNOWN`을 반환하며, 결제 완료로 간주하면 안 됩니다.

### 완료 결제 상세

```http
GET /api/payments/2
Authorization: Bearer <accessToken>
```

```json
{
  "paymentId": 2,
  "orderId": "pay_12345678-1234-1234-1234-123456789abc",
  "targetType": "RESERVATION",
  "targetId": 1,
  "orderName": "영화 티켓 예매",
  "amount": 28000,
  "currency": "KRW",
  "paymentMethod": "카드",
  "status": "DONE",
  "createdAt": "2026-11-03T21:00:00",
  "approvedAt": "2026-11-03T21:01:00"
}
```

`targetType`은 `RESERVATION / STORE_ORDER`입니다. `targetId`는 해당 예매 또는 매점 주문의 ID입니다. `approvedAt`은 Toss 승인 시각을 한국 시간으로 변환한 값이며, 응답의 `LocalDateTime`에는 시간대 표기가 없습니다.
`paymentMethod`는 승인 응답에서 저장하며, 필드 추가 이전 결제는 `null`일 수 있습니다. `orderName`은 현재 구매 종류별 고정 명칭을 결제 생성 시 저장합니다. 영화 제목이나 개별 메뉴 이름은 포함하지 않습니다.

### 완료 결제 목록

```http
GET /api/payments?page=0&size=20
Authorization: Bearer <accessToken>
```

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasNext": false
}
```

`content`에는 상세 응답과 같은 구조의 결제들이 들어갑니다. 페이지는 0부터 시작하고 `size`는 1~100입니다. `approvedAt DESC, paymentId DESC`로 정렬합니다. 다른 사용자 결제와 `DONE` 이외 상태는 제외합니다. 조회 과정에서는 Toss API를 호출하지 않습니다.

| 상황 | 응답 |
| --- | --- |
| 목록에 완료 결제가 없음 | 200, 빈 `content` |
| 상세 ID가 없거나 다른 사용자 결제이거나 미완료 결제 | 404 `PAYMENT_NOT_FOUND` |
| 음수 page 또는 범위를 벗어난 size | 400 `INVALID_PAYMENT_PAGE` |
| 인증 없음·유효하지 않은 토큰 | 401 |

## 영화 좋아요·영화관 즐겨찾기

| Method | 경로 | 권한 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/api/likes/movies/{movieId}` | 로그인 | 201 좋아요 |
| GET | `/api/likes/movies` | 로그인 | 200 본인 좋아요 목록 |
| DELETE | `/api/likes/movies/{movieId}` | 로그인 | 204 |
| POST | `/api/favorites/theaters/{theaterId}` | 로그인 | 201 즐겨찾기 |
| GET | `/api/favorites/theaters` | 로그인 | 200 본인 즐겨찾기 목록 |
| DELETE | `/api/favorites/theaters/{theaterId}` | 로그인 | 204 |

위 요청은 Body가 필요하지 않습니다.

## 현재 구현 범위

PG 환불·부분 취소·웹훅 수신·주기적인 PG 대사 작업은 아직 없습니다. 완료 결제 조회는 **우리 DB에 확정·저장된 승인 내역**을 반환합니다. PG 관리자 화면에서 별도로 취소한 결과까지 자동 반영하는 조회는 아닙니다.
