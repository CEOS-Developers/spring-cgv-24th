# 결제 전체·부분 취소 구현

## 기존 구조와의 관계

기존 승인 구조를 따라 `PaymentCancelService → PaymentCancelTransactionService.begin → TossPaymentClient → finish`로 구성했습니다. `payment` 패키지 안에서 처리하며 새 라이브러리나 외부 저장소를 추가하지 않았습니다.

1. `begin`: 본인 결제인지 확인하고 구매 대상 → Payment → 취소 이력 순서로 비관적 락을 획득합니다. 취소 가능 상태·잔액·동시 요청을 확인하고 요청 이력을 커밋합니다.
2. PG 호출: DB 트랜잭션 밖에서 Toss 취소 API를 호출합니다. 요청별 PG 멱등키와 확정 금액을 재사용합니다.
3. `finish`: 같은 순서로 다시 잠그고 PG의 결제 식별자·원금·통화·상태·잔액·전체 취소 이력을 검증합니다. 새 취소 거래 1건의 금액·사유와 기존 거래가 일치해야 합니다.
4. 검증 성공 시 결제 잔액·취소 이력을 저장합니다. 잔액이 0이면 예매/주문 취소와 좌석/재고 반환도 같은 트랜잭션에서 처리합니다.

구매 대상과 결제 락을 PG 응답 대기 중에는 유지하지 않습니다. 이 때문에 짧은 DB 트랜잭션으로 동시 요청을 제어하면서 느린 PG 응답을 기다릴 수 있습니다.

## API 사용

```http
POST /api/payments/{paymentId}/cancel
Authorization: Bearer <accessToken>
Idempotency-Key: 67512ba1-a798-4d85-a439-b091069b8001
Content-Type: application/json
```

부분 취소:

```json
{"cancelReason": "사용자 요청", "cancelAmount": 8000}
```

남은 금액 전체 취소:

```json
{"cancelReason": "사용자 요청"}
```

- `paymentId`는 승인 응답에 포함된 우리 결제 ID입니다.
- 금액은 정수이며 양수여야 합니다. 잔액 초과는 PG에 요청하기 전에 차단합니다.
- 사유는 공백이 아닌 1~200자입니다.
- 멱등키는 소문자·숫자·`_`·`-`로 이루어진 1~64자이며 UUID를 권장합니다.
- **같은 취소의 재시도에는 같은 키와 Body를 사용합니다.** 전체 취소 요청이었다면 재시도에서도 금액을 생략해야 합니다.
- 새 부분 취소를 할 때만 새로운 키를 생성합니다.
- 취소 이력: `GET /api/payments/{paymentId}/cancellations`
- 현재 결제 상태·잔액·누적 취소 금액: `GET /api/payments/{paymentId}`

응답·오류 코드는 [API 명세서](api-spec.md#전체부분-취소)에 정리했습니다. 조회 API는 Toss를 호출하지 않습니다.

## 금액과 상태

`Payment.amount`는 원금으로 보존하고 `balanceAmount`에 남은 금액을 저장합니다. `canceledAmount`는 `amount - balanceAmount`로 계산합니다. 승인 전에는 취소 가능한 승인 금액이 없어 잔액을 0으로 둡니다.

| 동작 | 최초 금액 | 잔액 | 누적 취소 금액 | 결제 상태 |
| --- | --- | --- | --- | --- |
| 최초 승인 | 28,000 | 28,000 | 0 | DONE |
| 8,000원 부분 취소 | 28,000 | 20,000 | 8,000 | PARTIAL_CANCELED |
| 5,000원 부분 취소 | 28,000 | 15,000 | 13,000 | PARTIAL_CANCELED |
| 나머지 전체 취소 | 28,000 | 0 | 28,000 | CANCELED |

취소 요청 자체의 상태는 `PaymentCancellation.status`로 별도 관리합니다.

| 취소 요청 상태 | 의미 |
| --- | --- |
| PROCESSING | 요청이 DB에 기록됐으며 결과 처리 전 |
| DONE | PG 취소 결과가 검증돼 로컬 반영 완료 |
| FAILED | PG의 명시적인 거절과 미변경 조회 결과 확인 |
| UNKNOWN | 통신 장애·검증 실패·비동기 진행 등으로 결과 미확정 |

취소가 미확정이면 결제의 마지막 확정 상태와 잔액을 유지합니다. 예를 들어 결제 `DONE`과 취소 요청 `UNKNOWN`이 함께 존재할 수 있습니다. 이 경우 이력에서 결과를 확인하고 같은 요청을 재시도해야 하며, 환불되지 않았다고 단정하면 안 됩니다.

## 중복·동시 요청

- 취소 요청은 `(payment_id, request_id)` UNIQUE, PG 취소 거래는 `transaction_key` UNIQUE로 중복 저장을 막습니다.
- 사용자 멱등키는 결제별로 관리하며 PG에는 별도의 UUID를 생성·저장합니다.
- 완료된 같은 요청은 저장된 결과를 반환하고 PG에 다시 요청하지 않습니다.
- 같은 키로 금액·사유를 변경하면 409 오류입니다.
- 전액 취소된 결제에 새로운 키로 요청하면 409 `PAYMENT_ALREADY_CANCELED`입니다.
- 진행 중·미확정 요청이 있으면 다른 키로 새 취소를 시작할 수 없습니다.
- 같은 요청이 동시에 PG에 전달되더라도 같은 PG 멱등키를 사용하며, 로컬 잔액 차감과 재고 반환은 한 번만 처리합니다.
- MySQL에서는 결제 락을 잡은 다음 취소 이력도 locking read로 조회하여 이전 트랜잭션 스냅샷으로 판단하지 않게 했습니다.

취소 응답과 이력에 있는 `balanceAmount, paymentStatus`는 현재 결제 전체의 값입니다. 이전 취소 요청을 재조회해도 해당 요청의 금액·거래는 유지되지만 현재 잔액은 이후 취소 결과까지 반영합니다.

## PG 오류와 복구

PG에서 오류가 발생하면 결제를 조회해 실제 결과를 확인합니다. 취소 응답이 유실됐어도 조회 결과로 취소 거래가 검증되면 정상 완료합니다.

- 명시적인 4xx 거절(408·409·429 제외) **및** 조회 결과에서 기존 거래·잔액 그대로임을 모두 확인: `FAILED`, HTTP 502
- 타임아웃, 5xx, 조회 실패, 응답 불일치, 처리 중 결과: `UNKNOWN`, HTTP 503
- UNKNOWN에서도 좌석·재고·잔액을 임의로 변경하지 않습니다.
- 재시도는 저장된 같은 PG 멱등키와 최초 확정 금액으로 요청합니다. 전체 취소도 PG 요청에는 확정된 잔액을 명시합니다.
- PG 멱등키 유효기간을 넘겨 재전송하면 추가 취소 위험이 있으므로, 생성 후 14일 이상 지난 미확정 요청은 PG 조회만 합니다. 해결되지 않으면 운영 확인이 필요합니다.
- PG 취소 성공 후 DB 저장이 실패해도 최초 요청 기록은 남습니다. 같은 요청을 재시도하여 검증·반영할 수 있습니다.

웹훅·자동 대사는 아직 없습니다. PG 관리자 화면에서 별도로 취소한 내역이 있거나 장기 미확정 요청이 남으면 수동 확인·별도 대사가 필요합니다. 검증하지 않은 PG 결과를 사용자 GET에서 반영하지 않습니다.

## 예매·매점 주문 정책

이번 부분 취소는 **금액 환불**입니다. 어느 좌석·품목을 취소했는지 선택하지 않으므로 부분 환불 중에는 예매 `Reserved`, 매점 주문 `PAID`와 좌석·재고를 유지합니다.

잔액이 0이 되면 예매 `Canceled`로 변경하고 모든 좌석을 반환하거나, 매점 주문 `CANCELED`로 변경하고 모든 주문 품목 재고를 한 번 반환합니다. 기존 미결제 취소 API의 역할은 유지합니다. 승인된 결제를 취소하려면 새 결제 취소 API를 사용하세요.

개별 좌석·품목 취소, 사용/수령 완료 후 환불 제한은 별도 정책과 데이터 연결이 필요합니다. 가상계좌는 환불 계좌 입력을 아직 지원하지 않아 이 API에서 차단합니다. 현재 테스트 대상은 카드 결제 흐름이며 PG 자체의 결제수단별 부분 취소 제한은 PG 응답으로 처리합니다. 비동기 취소는 DONE을 확인하기 전까지 미확정 상태로 유지합니다.

## DB와 테스트

[payment-cancellation-migration.sql](../scripts/sql/payment-cancellation-migration.sql)은 기존 로컬 `cgv_payment_test` DB용 일회성 스크립트입니다. `balance_amount`, 취소 이력 테이블, 부분 취소 상태 및 제약 조건을 추가합니다. 기존 DONE 결제 잔액은 원금으로 보완합니다.

이번 작업에서 이미 적용했고 기존 결제 2건을 보존했습니다. 같은 DB에 다시 실행하지 마세요. 서버는 `payment-test` 프로필로 재시작해야 변경된 API를 사용할 수 있습니다. 다른 DB에는 대상 스키마와 기존 상태를 확인한 후 적용해야 합니다.

```powershell
.\gradlew.bat test --tests '*PaymentCancelIntegrationTest' --tests '*TossPaymentCancelClientTest'
```

테스트는 전체·반복 부분 취소, 잔액 초과, 소유권, 중복·동시 요청, 타임아웃·거절·불일치 결과, 장기 미확정 조회, DB 실패 후 복구, 좌석·재고 반환을 검증합니다. PG는 모킹하며 클라이언트 요청 형식은 로컬 가짜 HTTP 서버로 검증합니다. 실제 Toss 결제를 취소하지 않습니다.

기본 테스트 DB는 H2입니다. 실제 MySQL 검증은 별도의 빈 `cgv_payment_cancel_it_*` DB에 한해 `PAYMENT_CANCEL_TEST_DB_URL`, `PAYMENT_CANCEL_TEST_DB_USERNAME`, `PAYMENT_CANCEL_TEST_DB_PASSWORD`로 지정할 수 있습니다. 이 테스트는 테이블을 생성·삭제하므로 기존 사용자 DB를 지정하면 안 되며 코드에서도 전용 DB 이름을 검사합니다.

공식 문서: [결제 취소 API](https://docs.tosspayments.com/reference), [전체·부분 취소 가이드](https://docs.tosspayments.com/guides/v2/cancel-payment), [멱등키 유효기간과 재시도](https://docs.tosspayments.com/reference/using-api/authorization).
