# 로컬 결제 테스트 DB

현재 엔드포인트는 [API 명세서](api-spec.md), 완료 결제 조회와 추가 DB 컬럼은 [결제 조회 설계](payment-history.md)를 참고하세요.

`cgv_payment_test`는 기존 `cgv_db`와 분리된 MySQL 테스트 DB입니다.
기존 DB에서는 테이블 정의만 복사하고 회원·주문·결제 데이터는 복사하지 않습니다.
`scripts/sql/payment-test-seed.sql`은 빈 테스트 DB에 한 번만 실행하는 초기 데이터입니다.

## 실행

IntelliJ의 Spring Boot 실행 설정에서 Active profiles를 `payment-test`로 지정합니다.
기존 `.env`의 `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`을 그대로 사용합니다.
같은 MySQL 서버에 접근할 수 있는 DB 계정이어야 합니다.

또는 프로젝트 루트의 PowerShell에서 실행합니다.

```powershell
.\gradlew.bat bootRun --args='--spring.profiles.active=payment-test'
```

- 서버: `http://localhost:8081`
- 주문서 화면: `http://localhost:8081/payment-test.html`
- 테이블 검증: `ddl-auto: validate` (재시작해도 데이터 보존)
- 프로필을 지정하지 않으면 기존 설정의 `cgv_db`와 `ddl-auto: create`가 적용됩니다.

## Postman 순서

1. `POST http://localhost:8081/api/auth/signup`으로 테스트 회원을 가입합니다.
2. `POST http://localhost:8081/api/auth/login`으로 새 토큰을 받습니다.
3. Bearer Token을 설정하고 아래 예매를 생성합니다.
4. 반환된 `reservationId`로 결제를 준비하거나 주문서 테스트 화면을 사용합니다.

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

초기 데이터는 영화 1개, 영화관 1개, 일반 상영관 1개, A1·A2 좌석,
초기화 시점 기준 내일 14시 상영회차, 평일·주말의 관람객별 요금 8개입니다.
성인 2명의 총액은 28,000원입니다. 예매 생성 후 10분 이내에 결제를 진행하세요.
동일 회차의 A1·A2가 이미 점유되었다면 본인 대기 예매를 취소하거나 만료 후 다시 시도하세요.

DB 초기화 스크립트 자체는 토스 API를 호출하지 않습니다.

매점 결제의 API·재고 정책·추가 DB 스키마는 [매점 주문 결제 안내](store-order-payment.md)를 참고하세요.
주문서 화면에서 구매 종류를 선택하면 영화 예매와 매점 주문을 모두 테스트할 수 있습니다.
