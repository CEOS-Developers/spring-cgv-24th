package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 결제 승인을 요청하고 응답을 읽는 역할을 함 .
@Component
public class TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TossConfirmResponse confirm(PaymentConfirmRequest request){
        TossConfirmResponse response = restClient.post()
                .uri("v1/payments/confirm")
                .header("Idempotency-Key","confirm-"+request.orderId())
                .body(request)
                .retrieve()
                .body(TossConfirmResponse.class);

        if(response==null){
            throw new IllegalStateException("Toss 결제 승인 응답이 비어있습니다.");
        }

        return response;
    }
}