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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Set;

// 토스 응답을 성공 / 거절 / 결과 모름으로 나누는 데까지만 한다. 그 결과로 우리 결제·예매 상태를 어떻게 바꿀지는
// 도메인이 정한다. 여기서 정하면 예매와 매점의 서로 다른 보상 규칙이 global로 새어 들어온다.
@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private static final String CONFIRM_URI = "/v1/payments/confirm";
    private static final String ORDER_LOOKUP_URI = "/v1/payments/orders/{orderId}";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    // 앞선 승인 요청이 이미 성공했다는 뜻이다. 그 응답만 놓친 경우라 거절로 처리하면 돈은 빠졌는데 실패로 기록된다.
    private static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";
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

    private final RestClient tossPaymentsRestClient;

    // 멱등키를 주문번호로 둔다. 같은 주문의 승인을 다시 보내도 토스가 첫 결과를 돌려줘 두 번 결제되지 않는다.
    public TossConfirmResult confirm(String paymentKey, String orderId, int amount) {
        try {
            TossPayment payment = tossPaymentsRestClient.post()
                    .uri(CONFIRM_URI)
                    .header(IDEMPOTENCY_KEY_HEADER, orderId)
                    .body(new ConfirmRequest(paymentKey, orderId, amount))
                    .retrieve()
                    .body(TossPayment.class);
            if (payment == null || !payment.isDone()) {
                // 카드·간편결제 승인은 DONE으로 끝난다. 그 밖의 상태(가상계좌 입금 대기 등)는 결과가 나중에 정해진다.
                return unknown(orderId, "승인 응답 상태가 DONE이 아님: " + (payment == null ? null : payment.status()));
            }
            return new Approved(payment);
        } catch (HttpClientErrorException e) {
            return fromClientError(orderId, e);
        } catch (RestClientException e) {
            // 5xx, 연결·읽기 타임아웃, 응답 본문 해석 실패. 토스가 승인했는지 이쪽에서는 알 수 없다.
            return unknown(orderId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 금액을 늘 명시한다. 빼면 토스가 남은 잔액 전부를 취소하므로, 우리 잔액과 토스 잔액이 어긋났을 때
    // 엉뚱한 금액이 조용히 환불된다. 명시하면 그때는 토스가 NOT_CANCELABLE_AMOUNT로 거절한다.
    public TossCancelResult cancel(String paymentKey, String idempotencyKey, int cancelAmount, String cancelReason) {
        try {
            TossPayment payment = tossPaymentsRestClient.post()
                    .uri(CANCEL_URI, paymentKey)
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(new CancelRequest(cancelReason, cancelAmount))
                    .retrieve()
                    .body(TossPayment.class);
            return fromCancelResponse(idempotencyKey, payment);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = parseError(e);
            if (CANCEL_UNKNOWN_CODES.contains(error.code())) {
                return cancelUnknown(idempotencyKey, error.code() + ": " + error.message());
            }
            log.warn("[Toss] 취소 거절 idempotencyKey={} status={} code={} message={}",
                    idempotencyKey, e.getStatusCode().value(), error.code(), error.message());
            return new TossCancelResult.Rejected(error.code(), error.message());
        } catch (RestClientException e) {
            return cancelUnknown(idempotencyKey, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // 같은 멱등키의 재요청에는 첫 응답이 그대로 오므로, 마지막 취소 거래가 곧 이 요청의 거래다.
    private TossCancelResult fromCancelResponse(String idempotencyKey, TossPayment payment) {
        if (payment == null || payment.cancels().isEmpty()
                || !(CANCELED.equals(payment.status()) || PARTIAL_CANCELED.equals(payment.status()))) {
            return cancelUnknown(idempotencyKey, "취소 응답 상태가 예상과 다름: " + (payment == null ? null : payment.status()));
        }
        TossPayment.Cancel cancel = payment.cancels().getLast();
        if (!DONE.equals(cancel.cancelStatus())) {
            return cancelUnknown(idempotencyKey, "취소 거래 상태가 DONE이 아님: " + cancel.cancelStatus());
        }
        return new TossCancelResult.Canceled(payment, cancel);
    }

    private TossCancelResult.Unknown cancelUnknown(String idempotencyKey, String reason) {
        log.error("[Toss] 취소 결과 불명 idempotencyKey={} reason={}", idempotencyKey, reason);
        return new TossCancelResult.Unknown(reason);
    }

    // paymentKey가 아니라 orderId로 찾는다. 승인 응답을 놓친 결제는 우리 DB에 paymentKey가 없다.
    public TossLookupResult findByOrderId(String orderId) {
        try {
            TossPayment payment = tossPaymentsRestClient.get()
                    .uri(ORDER_LOOKUP_URI, orderId)
                    .retrieve()
                    .body(TossPayment.class);
            if (payment == null) {
                return new Unavailable("조회 응답 본문 없음");
            }
            return new Found(payment);
        } catch (HttpClientErrorException e) {
            ErrorResponse error = parseError(e);
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return new NotFound(error.code(), error.message());
            }
            // 401·403 등은 결제가 없다는 뜻이 아니라 물어보지 못했다는 뜻이다
            return new Unavailable(error.code() + ": " + error.message());
        } catch (RestClientException e) {
            log.warn("[Toss] 결제 조회 실패 orderId={} reason={}", orderId, e.getMessage());
            return new Unavailable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private TossConfirmResult fromClientError(String orderId, HttpClientErrorException e) {
        ErrorResponse error = parseError(e);
        if (ALREADY_PROCESSED_PAYMENT.equals(error.code())) {
            return unknown(orderId, error.code());
        }
        log.warn("[Toss] 승인 거절 orderId={} status={} code={} message={}",
                orderId, e.getStatusCode().value(), error.code(), error.message());
        return new Rejected(error.code(), error.message());
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
