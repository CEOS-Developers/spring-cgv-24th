package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.service.PaymentFacade;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.config.SwaggerConfig;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
@SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
public class PaymentController {

	private final PaymentFacade paymentFacade;

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

	@Operation(summary = "토스 클라이언트 키 조회 API", description = "결제창 SDK 초기화에 사용하는 공개 클라이언트 키를 반환합니다.")
	@GetMapping("/client-key")
	public ApiResponse<String> getClientKey() {
		return ApiResponse.onSuccess(clientKey);
	}
}
