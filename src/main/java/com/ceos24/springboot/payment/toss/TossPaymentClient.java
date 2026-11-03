package com.ceos24.springboot.payment.toss;

import com.ceos24.springboot.payment.config.TossPaymentProperties;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// 우리 서버에서 토스 서버로 승인 및 조회 요청
@Component
public class TossPaymentClient {

    private final RestClient restClient;

    public TossPaymentClient(
            RestClient.Builder builder,
            TossPaymentProperties properties
    ) {
        if (!StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException(
                    "토스 API 주소와 시크릿 키 설정이 필요합니다."
            );
        }

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    headers.setBasicAuth(
                            properties.getSecretKey(),
                            "",
                            StandardCharsets.UTF_8
                    );
                    headers.setAccept(
                            java.util.List.of(MediaType.APPLICATION_JSON)
                    );
                })
                .build();
    }

    // 결제 승인 요청
    public TossPaymentConfirmResponse confirm(
            TossPaymentConfirmRequest request,
            String idempotencyKey
    ) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException(
                    "결제 승인 요청에 사용할 멱등키가 필요합니다."
            );
        }

        TossPaymentConfirmResponse response = restClient.post()
                .uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .body(request)
                .retrieve()
                .body(TossPaymentConfirmResponse.class);

        return requireResponse(response);
    }

    // 통신 오류 등으로 승인 결과가 불확실할 때 결제 조회
    public TossPaymentConfirmResponse findByPaymentKey(
            String paymentKey
    ) {
        TossPaymentConfirmResponse response = restClient.get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .body(TossPaymentConfirmResponse.class);

        return requireResponse(response);
    }

    private TossPaymentConfirmResponse requireResponse(
            TossPaymentConfirmResponse response
    ) {
        if (response == null) {
            throw new RestClientException(
                    "토스 API 응답 본문이 비어 있습니다."
            );
        }

        return response;
    }
}