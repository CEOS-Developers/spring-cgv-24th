package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossCancelResponse;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Map;

// 결제 승인을 요청하고 응답을 읽는 역할을 함 .
@Component
public class TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    //결제 승인
    public TossConfirmResponse confirm(PaymentConfirmRequest request){
        TossConfirmResponse response = restClient.post()
                .uri("/v1/payments/confirm")
                .header("Idempotency-Key","confirm-"+request.orderId())
                .body(request)
                .retrieve()
                .body(TossConfirmResponse.class);

        if(response==null){
            throw new IllegalStateException("Toss 결제 승인 응답이 비어있습니다.");
        }

        return response;
    }

    //결제 취소
    public TossCancelResponse cancelFull(
            String paymentKey,
            String cancelReason,
            String idempotencyKey
    ) {
        TossCancelResponse response = restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .body(Map.of("cancelReason", cancelReason))
                .retrieve()
                .body(TossCancelResponse.class);

        if (response == null) {
            throw new IllegalStateException("Toss 결제 취소 응답이 비어 있습니다.");
        }

        return response;
    }

    public TossCancelResponse cancelPartial(
            String paymentKey,
            int cancelAmount,
            String cancelReason,
            String idempotencyKey
    ) {
        if (cancelAmount <= 0) {
            throw new IllegalArgumentException("취소 금액은 0보다 커야 합니다.");
        }

        TossCancelResponse response = restClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .header("Idempotency-Key", idempotencyKey)
                .body(Map.of(
                        "cancelReason", cancelReason,
                        "cancelAmount", cancelAmount
                ))
                .retrieve()
                .body(TossCancelResponse.class);

        if (response == null) {
            throw new IllegalStateException("Toss 부분 취소 응답이 비어 있습니다.");
        }

        return response;
    }
}