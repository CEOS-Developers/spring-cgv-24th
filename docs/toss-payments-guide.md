# 토스페이먼츠 결제 연동 사전 정리 — 5주차

결제 연동을 시작하기 전에 토스페이먼츠 공식 문서 다섯 개를 읽고, 우리 서비스(CGV 클론)에 대입해 다시 정리한 문서다.

| # | 문서 | 이 문서의 위치 |
|---|---|---|
| 1 | [결제 흐름 이해하기](https://docs.tosspayments.com/guides/v2/get-started/payment-flow) | [1. 결제 흐름](#1-결제-흐름--요청--인증--승인) |
| 2 | [개발 환경 알아보기](https://docs.tosspayments.com/guides/v2/get-started/environment) | [2. 키와 테스트 환경](#2-키와-테스트-환경) |
| 3 | [Core API Reference](https://docs.tosspayments.com/reference) | [3. 서버가 부르는 API](#3-서버가-부르는-api) |
| 4 | [결제 연동하기 (결제위젯)](https://docs.tosspayments.com/guides/v2/payment-widget/integration) | [4. 브라우저 쪽 결제위젯](#4-브라우저-쪽-결제위젯) |
| 5 | [Webhook 연결하기](https://docs.tosspayments.com/guides/v2/webhook) | [5. 웹훅](#5-웹훅) |

- 키는 **결제위젯 테스트 키**(`test_gck_` / `test_gsk_`)를 쓴다. 전자결제 신청 전이라 문서 공용 키(`test_gck_docs_` / `test_gsk_docs_`)다.
- 결제 승인은 결제 세션 1~3, 결제 조회·취소(전체·부분)는 결제 세션 4에서 붙였다. 조회·취소 API 정리는 [3장](#결제-조회취소)에 있다.
- 마지막 [6. 과제 요구사항과 연결](#6-과제-요구사항과-연결)에 다음 세션에서 풀 설계 질문을 모았다.

---

## 1. 결제 흐름 — 요청 → 인증 → 승인

### 한 장 그림

```
 브라우저(구매자)              우리 서버(CGV)                   토스페이먼츠
      │                           │                               │
      │ ① 주문 만들기 ──────────▶  │ orderId·금액을 DB에 저장       │
      │ ◀──────── orderId, amount │                               │
      │                           │                               │
      │ ② requestPayment(orderId, amount, successUrl, failUrl) ──▶ │
      │                           │                               │
      │ ③ 카드·간편결제 인증 (구매자가 카드사/앱에서 본인 확인)  ◀──▶ │
      │                           │                               │  paymentKey 발급
      │ ◀──── successUrl?paymentKey=..&orderId=..&amount=.. ───── │  (아직 돈은 안 빠짐)
      │                           │                               │
      │ ④ 승인 요청 ────────────▶  │ DB의 주문과 대조               │
      │                           │ ⑤ POST /v1/payments/confirm ─▶│
      │                           │ ◀──────────── Payment(DONE) ──│  여기서 실제 결제
      │ ◀──────────── 결제 완료    │ 결제 정보 저장, 주문 상태 변경  │
```

| 단계 | 누가 | 하는 일 |
|---|---|---|
| 요청 | 브라우저 | SDK의 `requestPayment()`로 결제창을 연다. `orderId`, 금액, `successUrl`, `failUrl`을 넘긴다 |
| 인증 | 구매자 ↔ 카드사 | 카드번호 입력이나 간편결제 앱으로 "이 카드의 주인이 맞다"를 확인한다 |
| 승인 | 우리 서버 → 토스 | 인증이 끝난 결제를 **실제로 확정**한다. 이 호출이 성공해야 돈이 빠져나간다 |

### 요청과 승인을 왜 나눴나

**인증이 끝났다고 결제가 끝난 게 아니다.** 인증 후 토스는 `paymentKey`만 발급하고 기다린다. 결제를 확정할지 말지는 **우리 서버가 승인 API를 부르느냐**로 결정된다.

- 서버가 마지막 결정권을 갖는다. 금액이 조작됐거나, 좌석이 이미 팔렸거나, 주문이 만료됐으면 승인을 부르지 않으면 된다. 돈은 빠져나가지 않는다.
- 한 번에 처리하면(인증과 동시에 결제 확정) 서버는 결제 결과를 **나중에 비동기로** 알게 된다. 그러면 결과를 받기 위한 웹훅이 필수가 되고, 결제는 됐는데 우리 DB에는 기록이 없는 불일치가 생길 수 있다.
- 나눠 두면 승인 API의 **동기 응답**으로 결과를 바로 알 수 있다. 그래서 카드 결제만 다룰 때는 웹훅 없이도 정합성이 맞는다.

### 요청 전에 서버가 먼저 저장해야 하는 이유

`successUrl`로 돌아오는 `amount`는 **브라우저 주소창의 쿼리 파라미터**다. 구매자가 개발자 도구로 얼마든지 바꿀 수 있다.

```
/payments/success?paymentKey=tgen_xxx&orderId=cgv-r-17-abcd&amount=100   ← 원래 14000원
```

그래서 결제창을 열기 **전에** 서버가 `orderId`와 "받아야 할 금액"을 DB에 저장해 둔다. 승인 직전에 쿼리로 온 값과 DB 값을 대조하고, 다르면 승인을 부르지 않는다. 이 저장값이 위변조 검증의 **기준값**이다.

### 인증 후 10분 안에 승인해야 한다

> 결제 인증이 유효한 10분 안에 상점에서 결제 승인 API를 호출하지 않으면 해당 결제는 만료됩니다.

인증만 받고 승인을 안 부르면 그 결제는 토스 쪽에서 `EXPIRED`가 된다. 이후 승인을 부르면 실패한다.

**우리 서비스와 맞물리는 지점**: 예매 선점(`PENDING`)도 10분 뒤 만료된다(`expiresAt` = 선택 + 10분). 두 10분은 **기준 시각이 다르다.** 선점은 좌석을 고른 순간, 토스는 인증이 끝난 순간부터 센다. 좌석 선택 후 9분 만에 인증을 마치면 토스 쪽은 아직 유효하지만 선점은 1분 뒤 끝난다. 승인 직전에 **선점이 아직 살아 있는지** 우리 쪽에서 확인해야 한다.

### successUrl / failUrl로 돌아오는 값

| 리다이렉트 | 쿼리 파라미터 | 의미 |
|---|---|---|
| `successUrl` | `paymentKey` | 토스가 발급한 결제 식별자. 승인·조회·취소에 쓴다. **DB에 저장 필수** |
| | `orderId` | 우리가 넘긴 주문번호 그대로 |
| | `amount` | 결제 금액. **믿지 말고 DB와 대조** |
| | `paymentType` | `NORMAL` 등 결제 유형 |
| `failUrl` | `code`, `message` | 실패 사유. 예: 구매자가 결제창을 닫음 `PAY_PROCESS_CANCELED` |
| | `orderId` | 어느 주문의 실패인지 |

`successUrl`로 왔다는 것은 **인증 성공**이지 결제 성공이 아니다. 승인까지 성공해야 결제 성공이다.

`paymentKey`는 노출돼도 괜찮다. 시크릿 키 없이는 이 값으로 아무 API도 부를 수 없다.

---

## 2. 키와 테스트 환경

### 클라이언트 키 vs 시크릿 키

| | 클라이언트 키 | 시크릿 키 |
|---|---|---|
| 접두사 (결제위젯, 테스트) | `test_gck_` | `test_gsk_` |
| 쓰는 곳 | 브라우저 — SDK 초기화 `TossPayments(clientKey)` | 서버 — 토스 API 호출 시 인증 |
| 노출 | 괜찮다. 어차피 브라우저 소스에 들어간다 | **절대 안 된다.** 이 키만 있으면 남이 우리 가맹점으로 결제 승인·취소를 할 수 있다 |

> 시크릿 키는 외부에 노출되면 안 돼요. GitHub, 클라이언트 코드 등 외부에 보이는 곳에 추가하지 마세요.

우리 프로젝트는 JWT 서명키와 같은 방식으로 관리한다. `application.yaml`에는 `${TOSS_SECRET_KEY}` 자리표시자만 두고, 실제 값은 `.gitignore`에 등록된 `.env`로 주입한다.

### 키는 짝을 맞춰 써야 한다

키는 두 계열이 있고 **서로 섞을 수 없다.**

| 계열 | 클라이언트 키 | 시크릿 키 | 언제 |
|---|---|---|---|
| **결제위젯 연동 키** | `test_gck_` / `live_gck_` | `test_gsk_` / `live_gsk_` | 토스가 만든 결제 UI(위젯)를 붙일 때 ← **우리가 쓰는 것** |
| API 개별 연동 키 | `test_ck_` / `live_ck_` | `test_sk_` / `live_sk_` | 결제창 SDK를 직접 붙이거나 결제수단별로 계약할 때 |

위젯 클라이언트 키로 연 결제를 개별 연동 시크릿 키로 승인하거나, test 키와 live 키를 섞으면 `INVALID_API_KEY`가 난다.

### 키 받는 곳

[개발자센터](https://developers.tosspayments.com) 가입 → **API 키** 메뉴(`/my/api-keys`) → **결제위젯 연동 키** 탭.
가입만 한 상태에서는 "문서 예제와 같은 테스트 연동 키예요. 내 키는 전자결제 신청하고 확인할 수 있어요"라고 나오고 **문서 공용 키**만 보인다.
본인 상점 키는 전자결제를 신청해야(사업자 정보 필요) 받는다. 처음 이 문서를 쓸 때 "가입만 해도 본인 테스트 키가 나온다"고 적었는데 틀렸다(결제 세션 3에서 확인).

공용 키로 할 수 없는 것

- 개발자센터에서 **내 테스트 결제 내역**을 볼 수 없다. 결제 상태는 우리 DB와 결제 조회 API(`GET /v1/payments/{paymentKey}`)로 확인한다
- 웹훅을 등록할 수 없다(웹훅은 보류 중이라 당장 문제는 없다)
- 여러 사람이 같이 쓰는 상점이라 결제 정보가 남에게 보일 수 있다고 가정한다. 그래서 결제위젯에 회원 이름·이메일·전화번호를 넘기지 않는다(`customerKey`도 `ANONYMOUS`)

### 키가 맞는지 확인하는 법

결제를 만들지 않고도 확인할 수 있다. 없는 paymentKey로 조회 API를 불러 **인증 단계를 통과하는지**만 본다.

```bash
curl -s -u "$TOSS_SECRET_KEY:" https://api.tosspayments.com/v1/payments/tgen_setup_check_0000
```

| 응답 | 뜻 |
|---|---|
| 404 `NOT_FOUND_PAYMENT` (존재하지 않는 결제 정보) | **키 정상.** 인증은 통과했고 그 결제만 없다 |
| 401 `UNAUTHORIZED_KEY` | 키가 틀렸거나 콜론(`:`)이 빠졌다 |

처음에는 주문번호 조회(`/v1/payments/orders/{orderId}`)로 확인하고 404 `NOT_FOUND_MERCHANT`를 "키 정상, 그 주문만 없음"으로 적었다.
결제 세션 4에서 다시 보니 **문서 공용 키는 실제로 있는 결제도 주문번호로 조회하면 `NOT_FOUND_MERCHANT`를 준다.**
인증은 통과했다는 뜻은 맞지만 "주문 없음"으로 읽으면 안 된다. 공용 키로는 paymentKey 조회만 쓴다.

### 테스트 환경의 특징

- **결제가 승인돼도 실제로 돈이 빠지지 않는다.** 실제 카드번호를 넣어도 출금되지 않는다
- 카드사 인증 화면은 **실제 카드사 화면**(앱카드 푸시 포함)이 뜬다. 토스가 테스트용 국내 카드번호를 따로 주지 않고 본인 카드로 테스트하는 것을 전제로 한다.
  실제 카드 정보를 넣고 인증해도 승인은 가상으로 처리되어 출금되지 않는다(결제 세션 3에서 국민카드로 확인)
- 간편결제는 일부만 지원한다(토스페이·네이버페이 등). 카카오페이는 계약 후 MID로 받은 상점용 테스트 키로만 되고, 페이코는 테스트 키로 안 된다.
  문서 공용 키(`test_gck_docs_`)는 카카오페이까지 진행된다(결제 세션 3에서 확인)
- **간편결제 자동충전 주의**: 카카오페이 머니에 자동충전(연결 계좌에서 부족분 충전)이 걸려 있으면, 테스트 결제여도 **계좌 → 머니 충전은 실제로 일어난다.**
  결제 승인 자체는 가상이라 충전된 머니는 쓰이지 않고 남는다(결제 세션 3에서 42,000원 충전 후 머니로 남은 것을 확인). 테스트는 카드로 한다
- API 호출은 **분당 100건**으로 제한된다
- `TossPayments-Test-Code` 요청 헤더로 **에러를 일부러 재현**할 수 있다. 예를 들어 승인 API에 `TossPayments-Test-Code: REJECT_CARD_COMPANY`를 실으면 카드사 거절 응답이 온다. 실패 경로를 실제 카드 없이 시험할 수 있다
- HTTPS(TLS 1.2 이상)로만 통신한다

---

## 3. 서버가 부르는 API

### 공통

| 항목 | 값 |
|---|---|
| Base URL | `https://api.tosspayments.com` |
| 인증 | `Authorization: Basic {base64(시크릿키 + ":")}` |
| 본문 | `Content-Type: application/json` |
| 에러 응답 | `{"code": "...", "message": "..."}` + HTTP 상태 코드 |

**인증 헤더의 함정 — 콜론.** HTTP Basic 인증은 원래 `아이디:비밀번호`를 base64로 인코딩한다. 토스는 시크릿 키를 아이디 자리에 쓰고 비밀번호는 비운다. 그래서 **시크릿 키 뒤에 `:`를 붙인 뒤** 인코딩해야 한다. 콜론을 빼면 키가 맞아도 401이 난다.

```
시크릿 키       test_gsk_abc123
인코딩 대상      test_gsk_abc123:        ← 콜론
헤더            Authorization: Basic dGVzdF9nc2tfYWJjMTIzOg==
```

curl로 치면 `curl -u "test_gsk_abc123:"`과 같다.

**멱등키 `Idempotency-Key` 헤더.** 같은 요청이 두 번 가도 한 번만 처리되게 하는 키다. 승인 요청이 네트워크 타임아웃으로 응답을 못 받았을 때, 같은 멱등키로 다시 보내면 결제가 두 번 되지 않고 첫 결과가 돌아온다. 취소처럼 두 번 실행되면 곤란한 API에 특히 쓴다.

**타임아웃.** 토스 문서는 일부 API가 최대 60초 걸릴 수 있으니 타임아웃을 최소 60초로 두라고 권한다. 너무 짧게 잡으면 **토스에서는 승인됐는데 우리는 실패로 처리**하는 상황이 생긴다.

### 결제 승인 — 이번 과제의 핵심

```http
POST /v1/payments/confirm
Authorization: Basic {base64(시크릿키 + ":")}
Content-Type: application/json

{
  "paymentKey": "tgen_20240601...",
  "orderId": "cgv-r-17-abcd",
  "amount": 28000
}
```

| 필드 | 규칙 |
|---|---|
| `paymentKey` | 최대 200자. `successUrl`로 받은 값 |
| `orderId` | **6~64자, 영문 대소문자·숫자·`-`·`_`만.** 주문마다 고유해야 한다 |
| `amount` | 결제할 금액. 인증 때 금액과 다르면 토스도 거절한다 |

`orderId` 규칙은 우리 쪽 설계 제약이 된다. 예매 id `17`을 그대로 쓰면 6자 미만이라 거절되고, 같은 예매를 재결제할 때 같은 `orderId`를 다시 쓸 수도 없다. 주문번호를 따로 만들어야 한다.

**응답 — Payment 객체 중 저장할 만한 필드**

| 필드 | 의미 |
|---|---|
| `paymentKey` | 결제 식별자. 이후 조회·취소에 쓴다 |
| `orderId`, `orderName` | 주문번호, 주문명 |
| `status` | 결제 상태 (아래 표) |
| `method` | 결제수단. `카드`, `간편결제` 등 |
| `totalAmount` | 총 결제 금액 |
| `balanceAmount` | 취소 가능한 남은 금액. 부분 취소 시 줄어든다 |
| `requestedAt`, `approvedAt` | 결제 요청·승인 시각 (ISO 8601, `+09:00`) |
| `receipt.url` | 영수증 주소 |

**Payment `status` 값**

| 값 | 뜻 |
|---|---|
| `READY` | 결제를 만든 직후 |
| `IN_PROGRESS` | 인증 완료, 승인 대기 — `successUrl`로 돌아온 시점이 이 상태 |
| `WAITING_FOR_DEPOSIT` | 가상계좌 입금 대기 |
| `DONE` | **승인 완료** |
| `CANCELED` | 전액 취소 |
| `PARTIAL_CANCELED` | 부분 취소. **부분 취소를 거듭해 잔액이 0원이 돼도 이 상태로 남는다**(결제 세션 4에서 확인). 한 번에 전액을 취소해야 `CANCELED`가 된다 |
| `ABORTED` | 승인 실패 |
| `EXPIRED` | 10분 안에 승인하지 않아 만료 |

이 상태값은 **토스의 결제 상태**다. 우리 예매 상태(`PENDING`/`RESERVED`/…)와 1:1로 대응하지 않는다.
우리 `Payment`는 잔액이 0이면 `CANCELED`로 둔다. 그래서 우리 상태와 토스 상태를 맞춰 볼 때는 상태 이름이 아니라 `balanceAmount`와 `cancels`로 비교한다.

**승인 때 볼 만한 에러 코드**

| 코드 | 상황 |
|---|---|
| `ALREADY_PROCESSED_PAYMENT` | 이미 승인된 결제를 다시 승인 |
| `NOT_FOUND_PAYMENT_SESSION` | 인증 후 10분이 지나 결제 세션이 사라짐 |
| `REJECT_CARD_COMPANY` | 카드사 거절 (한도 초과, 정지 카드 등) |
| `INVALID_API_KEY` | 키 종류·환경이 맞지 않음 |
| `UNAUTHORIZED_KEY` | 시크릿 키가 틀렸거나 콜론 누락 |

### 결제 조회·취소

출처: [Core API 결제 취소·조회](https://docs.tosspayments.com/reference#결제-취소), [Payment 객체](https://docs.tosspayments.com/reference#payment-객체),
[취소 가이드](https://docs.tosspayments.com/guides/v2/cancel-payment), [멱등키](https://docs.tosspayments.com/reference/using-api/idempotency-key),
[에러 코드](https://docs.tosspayments.com/reference/error-codes)

**조회**

| API | 용도 |
|---|---|
| `GET /v1/payments/{paymentKey}` | paymentKey로 조회. 우리 관리자 대조 API는 paymentKey가 있으면 이것을 쓴다 |
| `GET /v1/payments/orders/{orderId}` | 주문번호로 조회. 승인 응답을 놓쳐 paymentKey가 없을 때의 수단이지만 **문서 공용 키에서는 늘 404 `NOT_FOUND_MERCHANT`** |

조회 에러 중 "그 결제가 토스에 없다"고 확신할 수 있는 것은 404 `NOT_FOUND_PAYMENT`뿐이다. 우리 클라이언트는 그 밖의 4xx·5xx·타임아웃을 "물어보지 못함"으로 분류한다.

**취소** — `POST /v1/payments/{paymentKey}/cancel`

| 필드 | 규칙 |
|---|---|
| `cancelReason` | 필수, 200자까지. 토스 결제 내역에 남는다. 우리는 서버가 만든다(`예매 부분 취소: A1, A2`) |
| `cancelAmount` | 빼면 남은 잔액 전부를 취소한다. 넣으면 그 금액만 부분 취소. 우리는 **전액 취소도 늘 넣는다**(잔액이 어긋났을 때 엉뚱한 금액이 환불되지 않게) |
| `refundReceiveAccount` | 가상계좌 환불용. 범위 밖 |

응답은 Payment 객체다. `status`가 `PARTIAL_CANCELED`/`CANCELED`로, `balanceAmount`(취소할 수 있는 잔액)가 줄고, `cancels[]`에 취소 거래가 쌓인다.

| `cancels[]` 필드 | 뜻 |
|---|---|
| `transactionKey` | 취소 거래 식별자 |
| `cancelAmount`, `cancelReason` | 이번 취소의 금액·사유 |
| `cancelStatus` | `DONE`이면 성공 |
| `canceledAt` | 취소 시각 |
| `refundableAmount` | 이 취소 뒤 남은 환불 가능 금액 |

**멱등키** — 모든 POST에 `Idempotency-Key` 헤더를 쓸 수 있다.

- 같은 키로 다시 보내면 다시 처리하지 않고 **첫 응답을 그대로** 준다. 첫 요청이 아직 처리 중이면 409 `IDEMPOTENT_REQUEST_PROCESSING`
- 키는 처음 쓴 날부터 **15일** 유효, 300자까지. GET에는 무시된다
- 같은 결제에 부분 취소를 여러 번 하면 주소·메서드가 같다. 그래서 키를 orderId로 두면 두 번째 취소에 첫 응답이 돌아온다. **취소 요청마다 새 키**를 쓴다

**취소 에러 중 기억할 것**

| 코드 | 우리 분류 | 이유 |
|---|---|---|
| 403 `NOT_CANCELABLE_AMOUNT`, `NOT_CANCELABLE_PAYMENT` 등 그 밖의 4xx | 거절 | |
| 400 `ALREADY_CANCELED_PAYMENT` | 결과 모름 | 앞선 취소가 성공하고 응답만 놓쳤을 수 있다 |
| 409 `IDEMPOTENT_REQUEST_PROCESSING` | 결과 모름 | 같은 키의 첫 요청이 처리 중 |
| 400 `PROVIDER_ERROR` | 결과 모름 | 400이지만 "일시적인 오류"라 처리됐는지 알 수 없다 |
| 500대(`FAILED_INTERNAL_SYSTEM_PROCESSING`, `FAILED_REFUND_PROCESS` 등) | 결과 모름 | |

결과를 모르는 취소를 거절로 처리하면, 다시 요청할 때 새 키로 같은 금액이 한 번 더 환불될 수 있다. 부분 취소는 잔액이 남아 있어 토스도 막지 않는다.

**취소 기한** — 카드는 제한이 없다(1년이 넘으면 카드사 사정으로 안 될 수 있음). 계좌이체 180일, 가상계좌 365일, 휴대폰은 당월.
가상계좌는 입금 전 전액 취소만 되고, 에스크로·현금 카드 결제는 부분 취소가 안 된다.

---

## 4. 브라우저 쪽 결제위젯

결제위젯은 토스가 만들어 둔 **결제수단 선택 UI + 약관 동의 UI**다. 우리는 화면에 자리만 만들어 두고 SDK를 부르면 된다.

```html
<script src="https://js.tosspayments.com/v2/standard"></script>

<div id="payment-method"></div>
<div id="agreement"></div>
<button id="pay">결제하기</button>
```

```js
const tossPayments = TossPayments(clientKey);            // test_gck_...
const widgets = tossPayments.widgets({ customerKey });   // 회원별 고유값, 비회원은 TossPayments.ANONYMOUS

await widgets.setAmount({ currency: "KRW", value: 28000 });
await widgets.renderPaymentMethods({ selector: "#payment-method", variantKey: "DEFAULT" });
await widgets.renderAgreement({ selector: "#agreement", variantKey: "AGREEMENT" });

document.getElementById("pay").addEventListener("click", () =>
  widgets.requestPayment({
    orderId: "cgv-r-17-abcd",          // 서버가 만들어 준 값
    orderName: "범죄도시4 외 1매",
    successUrl: location.origin + "/payments/success",
    failUrl: location.origin + "/payments/fail",
  })
);
```

| 단계 | 메서드 | 메모 |
|---|---|---|
| 초기화 | `TossPayments(clientKey)` → `widgets({ customerKey })` | `customerKey`는 UUID처럼 **추측 불가능한 고유값**. 이메일·전화번호 같은 값은 쓰지 않는다 |
| 금액 | `setAmount()` | 화면에 표시할 금액. 서버 저장 금액과 같아야 한다 |
| UI | `renderPaymentMethods()`, `renderAgreement()` | 둘 다 렌더링한 **뒤에** `requestPayment()`를 불러야 한다 |
| 결제 | `requestPayment()` | 결제창 → 인증 → `successUrl` 또는 `failUrl`로 이동 |

`setAmount`에 넣는 금액도 브라우저가 정한 값이다. 그래서 위젯을 쓰더라도 **최종 금액 검증은 서버**가 한다.

우리 프로젝트에는 프런트엔드가 없다. 실제로 `paymentKey`를 받아 보려면 위 코드를 담은 테스트용 HTML 페이지가 하나 필요하다. 다음 세션에서 승인 API와 함께 만든다.

---

## 5. 웹훅

### 무엇인가

결제 상태가 바뀌었을 때 **토스가 우리 서버로 먼저 알려 주는** HTTP POST 호출이다. 우리가 묻기 전에 토스가 알려 준다는 점이 승인 API(우리가 묻고 토스가 답한다)와 반대다.

| 이벤트 | 언제 |
|---|---|
| `PAYMENT_STATUS_CHANGED` | 결제 상태가 바뀔 때 (승인, 취소, 만료 등) |
| `DEPOSIT_CALLBACK` | 가상계좌 입금·입금 취소 |
| `CANCEL_STATUS_CHANGED` | 취소 상태가 바뀔 때 |

### 규칙

- 개발자센터 **웹훅** 메뉴에서 URL과 이벤트를 등록한다
- 우리 서버는 **10초 안에 200**을 돌려줘야 한다. 못 하면 실패로 보고 재전송한다
- 재전송은 최대 **7회**, 간격이 1 → 4 → 16 → 64 → 256 → 1024 → 4096분으로 늘어난다(약 3일 19시간)
- 같은 이벤트가 여러 번 올 수 있으므로 **두 번 받아도 결과가 같게**(멱등하게) 처리해야 한다
- 공개 URL이어야 한다. `localhost`로는 받을 수 없고 로컬에서 시험하려면 ngrok 같은 터널이 필요하다
- 웹훅 본문 자체를 그대로 믿기보다, 받은 `paymentKey`로 **조회 API를 다시 불러** 실제 상태를 확인하는 것이 안전하다

### 이번 과제에서 웹훅이 필요한가

**카드 결제 승인까지는 필수가 아니다.** 승인 결과는 승인 API의 동기 응답으로 바로 안다.

웹훅이 진짜 필요한 경우는 결과가 **나중에** 정해지는 결제다.

- 가상계좌 — 구매자가 은행에서 입금할 때까지 결과를 모른다
- 승인 요청은 보냈지만 타임아웃으로 응답을 못 받은 경우 — 토스에서는 승인됐을 수 있다

두 번째 경우는 웹훅 대신 조회 API로도 메울 수 있다. 다만 문서 공용 키는 주문번호 조회가 안 되므로, 승인을 시작할 때 paymentKey를 먼저 저장해 두어야 조회로 맞출 수 있다(결제 세션 5).

---

## 6. 과제 요구사항과 연결

| 과제 요구사항 | 문서에서 찾은 근거 |
|---|---|
| 결제가 필요한 기능 찾기 | 현재 mock 결제가 붙은 곳은 **예매**(`POST /api/reservations/{id}/payment`)와 **매점 구매**(`POST /api/purchases`) 두 곳 |
| 주문 정보를 생성·관리 | 결제창을 열기 **전에** 서버가 `orderId`와 금액을 저장해야 한다(1장) |
| 결제 정보를 DB에 저장, 상태 관리 | 승인 응답의 `paymentKey`, `status`, `method`, `approvedAt` 등(3장) |
| API Key를 환경변수로 | 시크릿 키 서버 전용, `.env` 주입(2장) |
| 클라이언트가 보낸 값을 믿어도 되나? | `amount`는 주소창 쿼리라 조작 가능. DB 기준값과 대조(1장) |
| 이미 결제된 주문에 다시 요청 | 토스도 `ALREADY_PROCESSED_PAYMENT`로 막지만, 우리 상태로 먼저 거르는 것이 맞다(3장) |

### 다음 세션에서 정할 것

1. **무엇에 결제를 붙이나** — 예매만? 매점도? 둘을 하나의 주문/결제 구조로 묶을 수 있나?
2. **주문은 어디에 두나** — `Reservation`이 곧 주문인가, 별도 주문(결제) 엔티티를 두나? `orderId` 규칙(6~64자, 고유)을 만족하는 주문번호는 누가 언제 만드나?
3. **승인 전 서버 검증 목록**
   - `orderId`로 찾은 주문이 **요청한 회원의 것**인가 (남의 주문 승인 방지)
   - 쿼리의 `amount`가 **서버가 저장한 금액**과 같은가
   - 주문이 아직 **결제 대기 상태**인가 (이미 결제됨 / 취소됨 / 만료됨이면 거절)
   - 예매라면 **선점이 아직 살아 있는가** (토스 10분과 우리 10분의 기준 시각이 다르다)
4. **승인 성공/실패 시 상태 전이** — 실패하면 좌석을 놓을까(현재 mock 실패는 즉시 해제)? 실패 코드별로 다르게 볼까?
5. **외부 호출과 트랜잭션** — 승인 API는 최대 수십 초 걸릴 수 있다. 그동안 DB 트랜잭션·락을 쥐고 있으면 안 된다(세션 5 매점 기록의 "실제 PG였다면" 메모와 같은 문제).
6. **승인은 됐는데 우리 DB 저장이 실패하면?** — 돈은 빠졌는데 기록이 없다. 조회 API로 복구할지, 취소 API로 되돌릴지.
7. **동시에 같은 주문으로 승인 요청이 두 번 오면?** — 우리 쪽 상태 검사만으로는 틈이 있다. 조건부 UPDATE·멱등키 중 무엇으로 막을지.
