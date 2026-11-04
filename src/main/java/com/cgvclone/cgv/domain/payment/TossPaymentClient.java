package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TossPaymentClient {
    private final String clientKey;
    private final String secretKey;
    private final RestClient client;

    @Autowired
    public TossPaymentClient(@Value("${toss.client-key:}") String clientKey,
                             @Value("${toss.secret-key:}") String secretKey) {
        this(clientKey, secretKey, buildClient(secretKey));
    }

    TossPaymentClient(String clientKey, String secretKey, RestClient client) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;
        this.client = client;
    }

    private static RestClient buildClient(String secretKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl("https://api.tosspayments.com")
                .requestFactory(factory).defaultHeaders(h -> h.setBasicAuth(secretKey, "")).build();
    }

    public String clientKey() {
        if (!clientKey.startsWith("test_gck_") || !secretKey.startsWith("test_gsk_")) {
            throw new GlobalException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        return clientKey;
    }

    public TossPayment confirm(PaymentConfirmRequest request) {
        clientKey();
        TossPayment payment;
        try {
            payment = client.post().uri("/v1/payments/confirm")
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(TossPayment.class);
        } catch (RestClientException exception) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        validate(payment, request);
        if (!("DONE".equals(payment.status()) && payment.approvedAt() != null)
                && !"WAITING_FOR_DEPOSIT".equals(payment.status())) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        return payment;
    }

    static void validate(TossPayment payment, PaymentConfirmRequest request) {
        if (payment == null || !request.orderId().equals(payment.orderId())
                || !request.paymentKey().equals(payment.paymentKey())
                || !request.amount().equals(payment.totalAmount()) || !"KRW".equals(payment.currency())) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
    }
}
