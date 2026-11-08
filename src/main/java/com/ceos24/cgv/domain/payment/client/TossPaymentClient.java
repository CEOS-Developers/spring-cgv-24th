package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.client.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossErrorResponse;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.exception.TossPaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentClient {

	private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
	private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";

	private final RestClient tossRestClient;

	public TossPaymentResponse confirm(String paymentKey, String orderId, int amount, String idempotencyKey) {
		return execute(() -> tossRestClient.post().uri("/v1/payments/confirm")
				.header(IDEMPOTENCY_HEADER, idempotencyKey).contentType(MediaType.APPLICATION_JSON)
				.body(new TossConfirmRequest(paymentKey, orderId, amount))
				.retrieve().body(TossPaymentResponse.class));
	}

	public TossPaymentResponse getByOrderId(String orderId) {
		return execute(() -> tossRestClient.get().uri("/v1/payments/orders/{orderId}", orderId)
				.retrieve().body(TossPaymentResponse.class));
	}

	// cancelAmount가 null이면 전액 취소
	public TossPaymentResponse cancel(String paymentKey, String cancelReason, Integer cancelAmount, String idempotencyKey) {
		return execute(() -> tossRestClient.post().uri("/v1/payments/{paymentKey}/cancel", paymentKey)
				.header(IDEMPOTENCY_HEADER, idempotencyKey).contentType(MediaType.APPLICATION_JSON)
				.body(new TossCancelRequest(cancelReason, cancelAmount))
				.retrieve().body(TossPaymentResponse.class));
	}

	private TossPaymentResponse execute(Supplier<TossPaymentResponse> request) {
		try {
			TossPaymentResponse response = request.get();
			if (response == null) {
				throw TossPaymentException.unknown("토스 응답 본문이 비어 있습니다.");
			}
			return response;
		} catch (HttpClientErrorException e) { // 4xx: 토스가 명확히 거절
			TossErrorResponse error = parseError(e);
			log.warn("토스 요청 거절 - status: {}, code: {}, message: {}", e.getStatusCode(), error.code(), error.message());
			if (IDEMPOTENT_REQUEST_PROCESSING.equals(error.code())) {
				throw TossPaymentException.unknown(error.message()); // 같은 요청이 아직 처리 중 → 결과 불명
			}
			throw TossPaymentException.rejected(error.code(), error.message());
		} catch (RestClientException e) { // 5xx, 타임아웃, 연결 실패, 응답 변환 실패
			log.error("토스 요청 결과 불명", e);
			throw TossPaymentException.unknown(e.getMessage());
		}
	}

	private TossErrorResponse parseError(HttpClientErrorException e) {
		try {
			TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
			return error != null ? error : new TossErrorResponse("UNKNOWN_ERROR", e.getMessage());
		} catch (RuntimeException parseFailure) {
			return new TossErrorResponse("UNKNOWN_ERROR", e.getMessage());
		}
	}
}
