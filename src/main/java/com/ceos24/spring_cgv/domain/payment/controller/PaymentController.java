package com.ceos24.spring_cgv.domain.payment.controller;

import com.ceos24.spring_cgv.domain.payment.dto.request.PaymentReadyRequest;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentConfirmResponse;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentInfoResponse;
import com.ceos24.spring_cgv.domain.payment.dto.response.PaymentReadyResponse;
import com.ceos24.spring_cgv.domain.payment.exception.code.PaymentSuccessCode;
import com.ceos24.spring_cgv.domain.payment.service.PaymentService;
import com.ceos24.spring_cgv.global.apipayload.ApiResponse;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "토스페이먼츠 결제 관련 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 요청 정보 사전 등록",
            description = "선택한 좌석의 재고를 검증하고 선점한 뒤, 토스 결제창에 필요한 주문 번호와 결제 금액을 반환합니다.")
    @PostMapping("/ready")
    public ApiResponse<PaymentReadyResponse> ready(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PaymentReadyRequest request
    ) {

        return ApiResponse.onSuccess(
                PaymentSuccessCode.PAYMENT_READY,
                paymentService.ready(userDetails.getMemberId(), request));
    }

    @Operation(summary = "토스 결제 성공 리다이렉트",
            description = "토스 결제창에서 결제를 마치면 토스가 브라우저를 이 주소로 보냅니다. "
                    + "좌석을 확정한 뒤 결제를 승인합니다. 브라우저 이동이라 인증 헤더가 없으므로, "
                    + "주문의 소유자는 저장된 결제 정보에서 확인합니다.")
    @SecurityRequirements
    @GetMapping("/toss/success")
    public ApiResponse<PaymentConfirmResponse> tossSuccess(
            @RequestParam String paymentKey,
            @RequestParam String orderId,
            @RequestParam Integer amount
    ) {

        return ApiResponse.onSuccess(
                PaymentSuccessCode.PAYMENT_CONFIRMED,
                paymentService.confirm(paymentKey, orderId, amount));
    }

    @Operation(summary = "토스 결제 실패 리다이렉트",
            description = "결제가 실패하거나 사용자가 결제창을 닫으면 토스가 이 주소로 보냅니다. "
                    + "주문을 중단 상태로 기록합니다.")
    @SecurityRequirements
    @GetMapping("/toss/fail")
    public ApiResponse<Void> tossFail(
            @RequestParam String code,
            @RequestParam String message,
            @RequestParam(required = false) String orderId
    ) {

        paymentService.fail(code, message, orderId);

        return ApiResponse.onSuccess(PaymentSuccessCode.PAYMENT_FAILED);
    }

    @Operation(summary = "완료된 결제 단건 조회",
            description = "승인이 완료된 결제 1건의 정보를 조회합니다.")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentInfoResponse> findPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId
    ) {

        return ApiResponse.onSuccess(
                PaymentSuccessCode.PAYMENT_FETCHED,
                paymentService.findPayment(userDetails.getMemberId(), paymentId));
    }
}
