package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossErrorResponse;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClientImpl implements TossPaymentsClient {

    private final RestClient tossRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public TossPaymentResponse confirm(TossConfirmRequest request, String idempotencyKey) {
        log.info(
                "Requesting Toss payment confirm: orderId={}, amount={}, idempotencyKey={}",
                request.orderId(),
                request.amount(),
                idempotencyKey);
        RestClient.RequestBodySpec requestSpec =
                tossRestClient.post().uri("/v1/payments/confirm").body(request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestSpec.header("Idempotency-Key", idempotencyKey);
        }

        try {
            return requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                    .body(TossPaymentResponse.class);
        } catch (ResourceAccessException e) {
            log.error("토스 결제 승인 API 네트워크 타임아웃/접속 실패: orderId={}, error={}", request.orderId(), e.getMessage());
            throw TossPaymentException.timeout("토스 결제 승인 API 호출 중 네트워크 타임아웃이 발생했습니다.", e);
        }
    }

    @Override
    public TossPaymentResponse getPayment(String paymentKey) {
        log.info("Requesting Toss payment inquiry: paymentKey={}", paymentKey);
        try {
            return tossRestClient
                    .get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                    .body(TossPaymentResponse.class);
        } catch (ResourceAccessException e) {
            log.error("토스 결제 조회 API 네트워크 타임아웃/접속 실패: paymentKey={}, error={}", paymentKey, e.getMessage());
            throw TossPaymentException.timeout("토스 결제 조회 API 호출 중 네트워크 타임아웃이 발생했습니다.", e);
        }
    }

    @Override
    public TossPaymentResponse cancel(String paymentKey, TossCancelRequest request, String idempotencyKey) {
        log.info(
                "Requesting Toss payment cancel: paymentKey={}, cancelAmount={}, idempotencyKey={}",
                paymentKey,
                request.cancelAmount(),
                idempotencyKey);
        RestClient.RequestBodySpec requestSpec = tossRestClient
                .post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(request);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            requestSpec.header("Idempotency-Key", idempotencyKey);
        }

        try {
            return requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                    .body(TossPaymentResponse.class);
        } catch (ResourceAccessException e) {
            log.error(
                    "토스 결제 취소 API 네트워크 타임아웃/접속 실패: paymentKey={}, cancelAmount={}, error={}",
                    paymentKey,
                    request.cancelAmount(),
                    e.getMessage());
            throw TossPaymentException.timeout("토스 결제 취소 API 호출 중 네트워크 타임아웃이 발생했습니다.", e);
        }
    }

    private void handleTossError(ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        TossErrorResponse error = null;
        try {
            byte[] body = response.getBody().readAllBytes();
            if (body.length > 0) {
                error = objectMapper.readValue(body, TossErrorResponse.class);
            }
        } catch (Exception e) {
            log.error("Failed to parse Toss error response body", e);
        }

        String code = error != null ? error.code() : "UNKNOWN_ERROR";
        String message = error != null ? error.message() : "토스 결제 대행사 통신 중 오류가 발생했습니다.";

        log.error("Toss API error response: httpStatus={}, code={}, message={}", status.value(), code, message);

        if (status.is4xxClientError()) {
            throw TossPaymentException.clientError(code, message);
        } else {
            throw TossPaymentException.serverError(code, message);
        }
    }
}
