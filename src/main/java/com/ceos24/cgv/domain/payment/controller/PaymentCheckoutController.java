package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.client.TossPaymentProperties;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCheckoutConfigResponse;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import com.ceos24.cgv.global.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "결제")
@SecurityRequirement(name = "bearerAuth")
public class PaymentCheckoutController {

    private final TossPaymentProperties properties;

    @GetMapping("/api/me/payments/checkout-config")
    @Operation(summary = "테스트 결제창 설정", description = "로그인한 사용자에게 SDK 초기화용 테스트 클라이언트 키만 반환합니다.")
    public ResponseEntity<ApiResponse<PaymentCheckoutConfigResponse>> getConfig() {
        // 과제용 화면에서 실결제가 일어나거나 위젯용 키를 다른 SDK 방식에 사용하는 것을 막는다.
        if (properties.clientKey() == null || !properties.clientKey().startsWith("test_ck_")
                || !properties.secretKey().startsWith("test_sk_")) {
            throw new BusinessException(ErrorCode.PAYMENT_CHECKOUT_NOT_CONFIGURED);
        }
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ApiResponse<>(
                new PaymentCheckoutConfigResponse(properties.clientKey()), code.getStatus(), code.getMessage()));
    }
}
