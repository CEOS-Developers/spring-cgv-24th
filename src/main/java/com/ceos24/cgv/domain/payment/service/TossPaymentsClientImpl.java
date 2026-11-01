package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossErrorResponse;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
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

        return requestSpec
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                .body(TossPaymentResponse.class);
    }

    @Override
    public TossPaymentResponse getPayment(String paymentKey) {
        log.info("Requesting Toss payment inquiry: paymentKey={}", paymentKey);
        return tossRestClient
                .get()
                .uri("/v1/payments/{paymentKey}", paymentKey)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                .body(TossPaymentResponse.class);
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

        return requestSpec
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> handleTossError(res))
                .body(TossPaymentResponse.class);
    }

    private void handleTossError(ClientHttpResponse response) {
        try {
            TossErrorResponse error = objectMapper.readValue(response.getBody(), TossErrorResponse.class);
            log.error("Toss API error response: code={}, message={}", error.code(), error.message());
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, error.message());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Toss error response", e);
            throw new BusinessException(ErrorCode.PAYMENT_GATEWAY_ERROR, "토스 결제 대행사 통신 중 오류가 발생했습니다.");
        }
    }
}
