package com.spring_cgv_24th.domain.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class TossPaymentGateway implements PaymentGateway {

    private static final Set<String> REJECTION_CODES = Set.of(
            "REJECT_CARD_PAYMENT", "REJECT_ACCOUNT_PAYMENT", "REJECT_CARD_COMPANY",
            "INVALID_REJECT_CARD", "INVALID_STOPPED_CARD", "INVALID_CARD_LOST_OR_STOLEN");

    private static final Set<String> CANCELLATION_REJECTION_CODES = Set.of(
            "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT", "NOT_ALLOWED_PARTIAL_REFUND",
            "NOT_ALLOWED_PARTIAL_REFUND_WAITING_DEPOSIT", "EXCEED_MAX_REFUND_DUE", "EXCEED_CANCEL_LIMIT");

    private final RestClient client;

    public TossPaymentGateway(RestClient client) {
        this.client = client;
    }

    @Override
    public Result confirm(String paymentKey, String orderId, long amount, String idempotencyKey) {
        return execute(() -> client.post().uri("/v1/payments/confirm")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ConfirmBody(paymentKey, orderId, amount))
                .retrieve().body(Result.class), REJECTION_CODES, false);
    }

    @Override
    public Result find(String paymentKey) {
        return execute(() -> client.get().uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve().body(Result.class), Set.of(), true);
    }

    @Override
    public Result cancel(String paymentKey, String cancelReason, Long cancelAmount, String idempotencyKey) {
        return execute(() -> client.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CancelBody(cancelReason, cancelAmount))
                .retrieve().body(Result.class), CANCELLATION_REJECTION_CODES, false);
    }

    private Result execute(Supplier<Result> request, Set<String> rejectionCodes, boolean lookup) {
        try {
            return request.get();
        } catch (RestClientResponseException error) {
            // 중복 처리·인증 오류·서버 오류를 결제 거절로 오인하지 않는다.
            TossError body;
            try {
                body = error.getResponseBodyAs(TossError.class);
            } catch (RuntimeException decodingFailure) {
                throw new Failure(false);
            }
            String code = body == null ? null : body.code();
            if (lookup && error.getStatusCode().value() == 404 && "NOT_FOUND_PAYMENT".equals(code)) {
                throw new Failure(Failure.Kind.NOT_FOUND);
            }
            throw new Failure(error.getStatusCode().is4xxClientError() && code != null && rejectionCodes.contains(code));
        } catch (RestClientException error) {
            throw new Failure(false);
        }
    }

    private record ConfirmBody(String paymentKey, String orderId, long amount) {
    }

    private record CancelBody(String cancelReason, @JsonInclude(JsonInclude.Include.NON_NULL) Long cancelAmount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossError(String code) {
    }
}
