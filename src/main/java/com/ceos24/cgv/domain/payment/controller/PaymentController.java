package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.config.SwaggerConfig;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment", description = "결제 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
public class PaymentController {

	private final PaymentFacade paymentFacade;
	private final PaymentService paymentService;

	@Value("${toss.client-key}")
	private String clientKey;

	@Operation(summary = "결제 승인 API", description = "토스 결제 인증 후 받은 paymentKey, orderId, amount를 서버에서 검증한 뒤 승인합니다.")
	@PostMapping("/confirm")
	public ApiResponse<PaymentResponse> confirm(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@Valid @RequestBody PaymentConfirmRequest request
	) {
		return ApiResponse.onSuccess(paymentFacade.confirm(userDetails.getUserId(), request));
	}

	@Operation(summary = "결제 조회 API", description = "본인의 결제 내역을 조회합니다. (서버 DB 기준)")
	@GetMapping("/{paymentId}")
	public ApiResponse<PaymentResponse> getPayment(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long paymentId
	) {
		return ApiResponse.onSuccess(paymentService.getPayment(userDetails.getUserId(), paymentId));
	}

	@Operation(summary = "결제 결과 대사 API", description = "결과 불명(UNKNOWN)이거나 승인 시작 후 5분이 지난(IN_PROGRESS) 결제를 토스에 조회해 승인 확정, 보상 취소 반영 또는 실패 처리합니다.")
	@PostMapping("/{paymentId}/reconcile")
	public ApiResponse<PaymentResponse> reconcile(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long paymentId
	) {
		return ApiResponse.onSuccess(paymentFacade.reconcile(userDetails.getUserId(), paymentId));
	}

	@Operation(summary = "결제 취소 API", description = "결제를 전체 또는 부분 취소합니다. 금액을 생략하면 취소 가능 잔액 전액을 취소하며, Idempotency-Key 헤더가 필수입니다(최대 300자, confirm-·compensate-로 시작하는 키 불가).")
	@PostMapping("/{paymentId}/cancel")
	public ApiResponse<PaymentResponse> cancel(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PathVariable Long paymentId,
			@RequestHeader("Idempotency-Key") String idempotencyKey,
			@Valid @RequestBody PaymentCancelRequest request
	) {
		return ApiResponse.onSuccess(paymentFacade.cancel(userDetails.getUserId(), paymentId, idempotencyKey, request));
	}

	@Operation(summary = "토스 클라이언트 키 조회 API", description = "결제창 SDK 초기화에 사용하는 공개 클라이언트 키를 반환합니다.")
	@GetMapping("/client-key")
	public ApiResponse<String> getClientKey() {
		return ApiResponse.onSuccess(clientKey);
	}
}
