package com.ceos24.cgv.domain.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ceos24.cgv.domain.payment.client.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class TossPaymentClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper = TossPaymentConfig.jsonMapper();

    public TossPaymentClient(@Qualifier("tossRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public TossPaymentResponse confirm(TossConfirmRequest request, String pgIdempotencyKey) {
        Objects.requireNonNull(request, "승인 요청은 필수입니다.");
        validateIdempotencyKey(pgIdempotencyKey);
        TossPaymentResponse response = execute(() -> restClient.post().uri("/v1/payments/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pgIdempotencyKey)
                .body(request).retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(TossPaymentResponse.class));
        if (!request.paymentKey().equals(response.paymentKey())
                || !request.orderId().equals(response.orderId()) || request.amount() != response.totalAmount()) {
            throw TossPaymentException.invalidResponse(null, null);
        }
        return response;
    }

    public TossPaymentResponse getPayment(String paymentKey) {
        validatePaymentKey(paymentKey);
        TossPaymentResponse response = execute(() -> restClient.get().uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve().onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(TossPaymentResponse.class));
        validateResponseKey(paymentKey, response);
        return response;
    }

    public TossPaymentResponse cancel(String paymentKey, TossCancelRequest request, String pgIdempotencyKey) {
        validatePaymentKey(paymentKey);
        Objects.requireNonNull(request, "취소 요청은 필수입니다.");
        validateIdempotencyKey(pgIdempotencyKey);
        TossPaymentResponse response = execute(() -> restClient.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", pgIdempotencyKey)
                .body(request).retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), this::handleError)
                .body(TossPaymentResponse.class));
        validateResponseKey(paymentKey, response);
        return response;
    }

    private TossPaymentResponse execute(Supplier<TossPaymentResponse> call) {
        try {
            TossPaymentResponse response = call.get();
            if (response == null || response.paymentKey() == null || response.paymentKey().isBlank()
                    || response.paymentKey().length() > 200
                    || response.orderId() == null || response.orderId().isBlank() || response.orderId().length() > 64
                    || response.status() == null || response.status().isBlank()
                    || !"KRW".equals(response.currency())
                    || response.totalAmount() == null || response.totalAmount() <= 0
                    || response.balanceAmount() == null || response.balanceAmount() < 0
                    || response.balanceAmount() > response.totalAmount()) {
                throw TossPaymentException.invalidResponse(null, null);
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw TossPaymentException.communicationError(exception);
        } catch (RestClientException exception) {
            for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
                if (cause instanceof IOException) {
                    throw TossPaymentException.communicationError(exception);
                }
            }
            throw TossPaymentException.invalidResponse(null, exception);
        }
    }

    private void handleError(HttpRequest request, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (!response.getStatusCode().isError()) {
            throw TossPaymentException.invalidResponse(status, null);
        }
        TossErrorResponse error;
        try {
            error = jsonMapper.readValue(response.getBody(), TossErrorResponse.class);
        } catch (JacksonException exception) {
            throw TossPaymentException.invalidResponse(status, exception);
        }
        if (error == null || error.code() == null || error.code().isBlank()
                || error.message() == null || error.message().isBlank()) {
            throw TossPaymentException.invalidResponse(status, null);
        }
        throw TossPaymentException.apiError(status, error.code(), error.message());
    }

    private void validateResponseKey(String paymentKey, TossPaymentResponse response) {
        if (!paymentKey.equals(response.paymentKey())) {
            throw TossPaymentException.invalidResponse(null, null);
        }
    }

    private void validatePaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200) {
            throw new IllegalArgumentException("결제 키는 필수이며 200자 이하여야 합니다.");
        }
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 300
                || key.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new IllegalArgumentException("PG 멱등키는 제어 문자가 없는 300자 이하의 값이어야 합니다.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossErrorResponse(String code, String message) {
    }
}
