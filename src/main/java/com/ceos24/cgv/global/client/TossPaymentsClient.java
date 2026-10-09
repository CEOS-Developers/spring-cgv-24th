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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String code, String message) {
    }
}
