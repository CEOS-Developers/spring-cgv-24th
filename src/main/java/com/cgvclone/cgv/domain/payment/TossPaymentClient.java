package com.cgvclone.cgv.domain.payment;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import java.time.Duration;
import java.net.SocketTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
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
        long started = System.nanoTime();
        TossPayment payment;
        try {
            payment = client.post().uri("/v1/payments/confirm")
                    .header("Idempotency-Key", "payment-confirm:" + request.orderId())
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(TossPayment.class);
        } catch (RestClientResponseException exception) {
            logFailure(started, "PROVIDER_HTTP_ERROR", exception.getStatusCode().value());
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        } catch (RestClientException exception) {
            logFailure(started, isTimeout(exception) ? "NETWORK_TIMEOUT" : "TRANSPORT_ERROR", null);
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        try {
            validate(payment, request);
        } catch (GlobalException exception) {
            logFailure(started, "RESPONSE_MISMATCH", null);
            throw exception;
        }
        if (!("DONE".equals(payment.status()) && payment.approvedAt() != null)
                && !"WAITING_FOR_DEPOSIT".equals(payment.status())) {
            logFailure(started, "UNEXPECTED_PAYMENT_STATUS", null);
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
        log.atInfo().addKeyValue("event", "payment.provider.result")
                .addKeyValue("provider", "toss")
                .addKeyValue("outcome", "DONE".equals(payment.status()) ? "approved" : "waiting_for_deposit")
                .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                .log("Payment provider result received");
        return payment;
    }

    private void logFailure(long started, String reason, Integer httpStatus) {
        // 외부 예외의 메시지/본문에는 결제 키가 포함될 수 있어 출력하지 않는다.
        log.atError().addKeyValue("event", "payment.provider.result")
                .addKeyValue("provider", "toss").addKeyValue("outcome", "unknown")
                .addKeyValue("failureReason", reason).addKeyValue("providerHttpStatus", httpStatus)
                .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                .log("Payment provider result could not be confirmed");
    }

    private boolean isTimeout(Throwable exception) {
        for (int depth = 0; exception != null && depth < 8; depth++, exception = exception.getCause()) {
            if (exception instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    static void validate(TossPayment payment, PaymentConfirmRequest request) {
        if (payment == null || !request.orderId().equals(payment.orderId())
                || !request.paymentKey().equals(payment.paymentKey())
                || !request.amount().equals(payment.totalAmount()) || !"KRW".equals(payment.currency())) {
            throw new GlobalException(ErrorCode.PAYMENT_UNAVAILABLE);
        }
    }
}
