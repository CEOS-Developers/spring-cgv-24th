package com.ceos24.cgv.global.client;

import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

// 토스 응답을 성공 / 거절 / 결과 모름으로 나누는 데까지만 한다. 그 결과로 우리 결제·예매 상태를 어떻게 바꿀지는
// 도메인이 정한다. 여기서 정하면 예매와 매점의 서로 다른 보상 규칙이 global로 새어 들어온다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private static final String CONFIRM_URI = "/v1/payments/confirm";
    private static final String ORDER_LOOKUP_URI = "/v1/payments/orders/{orderId}";
    private static final String PAYMENT_KEY_LOOKUP_URI = "/v1/payments/{paymentKey}";
    // 조회에서 "토스에 그 결제가 없다"고 확신할 수 있는 유일한 응답이다.
    private static final String NOT_FOUND_PAYMENT = "NOT_FOUND_PAYMENT";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    // 4xx지만 토스가 승인하지 않았다고 확신할 수 없는 응답들이다.
    // ALREADY_PROCESSED_PAYMENT: 앞선 승인 요청이 이미 성공했고 그 응답만 놓쳤다. 거절로 처리하면 돈은 빠졌는데 실패로 기록된다.
    // PROVIDER_ERROR: 400이지만 "일시적인 오류"라 카드사에서 처리됐는지 알 수 없다. 취소에서와 같은 기준이다.
    // IDEMPOTENT_REQUEST_PROCESSING: 같은 멱등키(주문번호)의 첫 승인 요청이 아직 처리 중이다. 그 요청은 승인될 수 있다.
    // 그 밖의 4xx는 거절로 둔다. 카드 한도 초과 같은 흔한 거절까지 결과 불명으로 두면 회원이 정리까지 다시 결제하지 못한다.
    // 새 코드는 "이미 처리됨·처리 중·일시 오류" 계열인지로 판단해 여기에 더한다.
    private static final Set<String> CONFIRM_UNKNOWN_CODES =
            Set.of("ALREADY_PROCESSED_PAYMENT", "PROVIDER_ERROR", "IDEMPOTENT_REQUEST_PROCESSING");
    private static final String CANCEL_URI = "/v1/payments/{paymentKey}/cancel";
    private static final String DONE = "DONE";
    private static final String CANCELED = "CANCELED";
    private static final String PARTIAL_CANCELED = "PARTIAL_CANCELED";
    // 4xx지만 토스가 취소하지 않았다고 확신할 수 없는 응답들이다.
    // ALREADY_CANCELED_PAYMENT: 앞선 취소가 성공했는데 그 응답만 놓쳤을 수 있다.
    // IDEMPOTENT_REQUEST_PROCESSING: 같은 멱등키의 첫 요청이 아직 처리 중이다.
    // PROVIDER_ERROR: 400이지만 "일시적인 오류"라 결제사 쪽에서 처리됐는지 알 수 없다.
    private static final Set<String> CANCEL_UNKNOWN_CODES =
            Set.of("ALREADY_CANCELED_PAYMENT", "IDEMPOTENT_REQUEST_PROCESSING", "PROVIDER_ERROR");
    // 토스가 결제를 판단하기 전에 막은 응답이다. 우리 키 설정이 틀렸거나 호출 한도를 넘었다.
    private static final Set<String> SERVICE_ERROR_CODES = Set.of("UNAUTHORIZED_KEY", "INVALID_API_KEY");
    private static final String CONNECTION_FAILED = "CONNECTION_FAILED";

    private final RestClient tossPaymentsRestClient;

    // 멱등키를 주문번호로 둔다. 같은 주문의 승인을 다시 보내도 토스가 첫 결과를 돌려줘 두 번 결제되지 않는다.
    public TossConfirmResult confirm(String paymentKey, String orderId, int amount) {
        try {
            TossPayment payment = withConnectRetry(orderId, () -> tossPaymentsRestClient.post()
                    .uri(CONFIRM_URI)
                    .header(IDEMPOTENCY_KEY_HEADER, orderId)
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPayment.class));
            if (payment == null) {
                return unknown(orderId, "승인 응답 본문 없음");
            }
            // 2xx라도 다른 결제의 응답이면 그 내용으로 확정할 수 없다. 돈은 움직였을 수 있으므로 거절이 아니다.
            if (!paymentKey.equals(payment.paymentKey()) || !orderId.equals(payment.orderId())
                    || payment.totalAmount() != amount) {
                return unknown(orderId, "승인 응답이 요청과 다름: paymentKey=%s orderId=%s totalAmount=%d"
                        .formatted(payment.paymentKey(), payment.orderId(), payment.totalAmount()));
            }
            if (!payment.isDone()) {
                // 카드·간편결제 승인은 DONE으로 끝난다. 그 밖의 상태(가상계좌 입금 대기 등)는 결과가 나중에 정해진다.
                return unknown(orderId, "승인 응답 상태가 DONE이 아님: " + payment.status());
            }
            return new Approved(payment);
        } catch (HttpClientErrorException e) {
            return fromClientError(orderId, e);
        } catch (RestClientException e) {
            // 승인은 주문번호마다 한 번만 보낸다(같은 주문의 재승인은 T1이 막는다). 그래서 연결조차 못 했으면
            // 이 주문의 승인은 토스에 한 번도 닿지 않은 것이고, 돈이 빠지지 않았다고 확신할 수 있다.
            if (isNotSent(e)) {
                log.error("[Toss] 승인 요청 연결 실패 orderId={} reason={}", orderId, e.getMessage());
                return new Rejected(CONNECTION_FAILED, e.getMessage(), true);
            }
            // 5xx, 읽기 타임아웃, 응답 본문 해석 실패. 토스가 승인했는지 이쪽에서는 알 수 없다.
            return unknown(orderId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 금액을 늘 명시한다. 빼면 토스가 남은 잔액 전부를 취소하므로, 우리 잔액과 토스 잔액이 어긋났을 때
    // 엉뚱한 금액이 조용히 환불된다. 명시하면 그때는 토스가 NOT_CANCELABLE_AMOUNT로 거절한다.
    public TossCancelResult cancel(String paymentKey, String idempotencyKey, int cancelAmount, String cancelReason) {
        try {
            TossPayment payment = withConnectRetry(idempotencyKey, () -> tossPaymentsRestClient.post()
                    .uri(CANCEL_URI, paymentKey)
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(new CancelRequest(cancelReason, cancelAmount))
                    .retrieve()
                    .body(TossPayment.class));
            return fromCancelResponse(paymentKey, idempotencyKey, cancelAmount, payment);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = parseError(e);
            if (CANCEL_UNKNOWN_CODES.contains(error.code())) {
                return cancelUnknown(idempotencyKey, error.code() + ": " + error.message());
            }
            // 취소는 재시도일 수 있다. 키 오류로 이번 요청이 막혔다고 해서 같은 키의 첫 요청이 환불되지 않았다는 뜻은 아니다.
            if (isServiceError(e, error)) {
                return cancelUnknown(idempotencyKey, "토스 호출 불가 " + error.code() + ": " + error.message());
            }
            log.warn("[Toss] 취소 거절 idempotencyKey={} status={} code={} message={}",
                    idempotencyKey, e.getStatusCode().value(), error.code(), error.message());
            return new TossCancelResult.Rejected(error.code(), error.message());
        } catch (RestClientException e) {
            // 연결 실패도 승인과 달리 결과 불명이다. 재시도라면 첫 요청이 이미 환불됐을 수 있다.
            return cancelUnknown(idempotencyKey, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 같은 멱등키의 재요청에는 첫 응답이 그대로 오므로, 마지막 취소 거래가 곧 이 요청의 거래다.
    private TossCancelResult fromCancelResponse(String paymentKey, String idempotencyKey, int cancelAmount,
                                                TossPayment payment) {
        if (payment == null || payment.cancels().isEmpty()
                || !(CANCELED.equals(payment.status()) || PARTIAL_CANCELED.equals(payment.status()))) {
            return cancelUnknown(idempotencyKey, "취소 응답 상태가 예상과 다름: " + (payment == null ? null : payment.status()));
        }
        TossPayment.Cancel cancel = payment.cancels().getLast();
        // 다른 결제나 다른 금액의 거래로 확정하면 우리 누적 취소액과 좌석·수량이 실제 환불과 어긋난다.
        if (!paymentKey.equals(payment.paymentKey()) || cancel.cancelAmount() != cancelAmount) {
            return cancelUnknown(idempotencyKey, "취소 응답이 요청과 다름: paymentKey=%s cancelAmount=%d"
                    .formatted(payment.paymentKey(), cancel.cancelAmount()));
        }
        if (!DONE.equals(cancel.cancelStatus())) {
            return cancelUnknown(idempotencyKey, "취소 거래 상태가 DONE이 아님: " + cancel.cancelStatus());
        }
        return new TossCancelResult.Canceled(payment, cancel);
    }

    private TossCancelResult.Unknown cancelUnknown(String idempotencyKey, String reason) {
        log.error("[Toss] 취소 결과 불명 idempotencyKey={} reason={}", idempotencyKey, reason);
        return new TossCancelResult.Unknown(reason);
    }

    public TossLookupResult findByPaymentKey(String paymentKey) {
        return lookup(PAYMENT_KEY_LOOKUP_URI, paymentKey, TossPayment::paymentKey);
    }

    // 승인 응답을 놓친 결제는 우리 DB에 paymentKey가 없어 orderId로 찾는다.
    // 문서 공용 테스트 키는 실제로 있는 결제도 orderId로는 404 NOT_FOUND_MERCHANT를 준다(결제 세션 4에서 확인).
    public TossLookupResult findByOrderId(String orderId) {
        return lookup(ORDER_LOOKUP_URI, orderId, TossPayment::orderId);
    }

    // keyOf: 응답이 정말 물어본 결제의 것인지 확인할 값. 다른 결제의 응답으로 대조·정리하면 엉뚱한 결제를 확정하게 된다.
    private TossLookupResult lookup(String uri, String key, Function<TossPayment, String> keyOf) {
        try {
            TossPayment payment = withConnectRetry(key, () -> tossPaymentsRestClient.get()
                    .uri(uri, key)
                    .retrieve()
                    .body(TossPayment.class));
            if (payment == null) {
                return new Unavailable("조회 응답 본문 없음");
            }
            if (!Objects.equals(key, keyOf.apply(payment))) {
                log.error("[Toss] 조회 응답이 요청과 다름 key={} paymentKey={} orderId={}",
                        key, payment.paymentKey(), payment.orderId());
                return new Unavailable("조회 응답이 요청한 결제가 아님");
            }
            return new Found(payment);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = parseError(e);
            if (NOT_FOUND_PAYMENT.equals(error.code())) {
                return new NotFound(error.code(), error.message());
            }
            // 404여도 NOT_FOUND_MERCHANT처럼 결제가 없다는 뜻이 아닌 응답이 있다. 401·403도 물어보지 못했다는 뜻이다
            return new Unavailable(error.code() + ": " + error.message());
        } catch (RestClientException e) {
            log.warn("[Toss] 결제 조회 실패 key={} reason={}", key, e.getMessage());
            return new Unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private TossConfirmResult fromClientError(String orderId, HttpClientErrorException e) {
        ErrorResponse error = parseError(e);
        if (CONFIRM_UNKNOWN_CODES.contains(error.code())) {
            return unknown(orderId, error.code() + ": " + error.message());
        }
        if (isServiceError(e, error)) {
            // 회원이 아니라 운영자가 고쳐야 하는 문제다. warn으로 남기면 거절 로그에 묻힌다.
            log.error("[Toss] 승인 호출 불가 orderId={} status={} code={} message={}",
                    orderId, e.getStatusCode().value(), error.code(), error.message());
            return new Rejected(error.code(), error.message(), true);
        }
        log.warn("[Toss] 승인 거절 orderId={} status={} code={} message={}",
                orderId, e.getStatusCode().value(), error.code(), error.message());
        return new Rejected(error.code(), error.message(), false);
    }

    private boolean isServiceError(HttpClientErrorException e, ErrorResponse error) {
        int status = e.getStatusCode().value();
        return SERVICE_ERROR_CODES.contains(error.code()) || status == 401 || status == 429;
    }

    // 연결조차 못 한 요청은 토스에 닿지 않았으므로 한 번 다시 보낸다. 멱등키가 같아 혹시 닿았더라도 두 번 처리되지 않는다.
    // 읽기 타임아웃은 다시 보내지 않는다. 이미 최대 60초를 기다렸고, 토스가 처리 중이라면 다시 보내도 결과를 더 빨리 알 수 없다.
    private <T> T withConnectRetry(String key, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            if (!isNotSent(e)) {
                throw e;
            }
            log.warn("[Toss] 연결 실패, 한 번 다시 보냄 key={} reason={}", key, e.getMessage());
            return call.get();
        }
    }

    // TCP 연결 전에 실패한 경우만 고른다. 연결된 뒤의 실패는 요청이 토스에 닿았을 수 있다.
    private static boolean isNotSent(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException
                    || cause instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    // 4xx는 본문 형식이 어긋나도 토스가 요청을 받아들이지 않았다는 사실은 같다. 거절로 두고 사유만 비운다.
    private ErrorResponse parseError(HttpClientErrorException e) {
        try {
            ErrorResponse error = e.getResponseBodyAs(ErrorResponse.class);
            if (error != null && error.code() != null) {
                return error;
            }
        } catch (RuntimeException ignored) {
            // 아래 기본값으로 대신한다
        }
        return new ErrorResponse("HTTP_" + e.getStatusCode().value(), e.getStatusText());
    }

    private Unknown unknown(String orderId, String reason) {
        log.error("[Toss] 승인 결과 불명 orderId={} reason={}", orderId, reason);
        return new Unknown(reason);
    }

    record ConfirmRequest(String paymentKey, String orderId, int amount) {
    }

    record CancelRequest(String cancelReason, int cancelAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String code, String message) {
    }
}
