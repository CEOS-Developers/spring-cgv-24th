package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCommandResponse;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/payments")
@Tag(name = "결제", description = "매점 주문·영화 예매 공통 결제 승인, 조회 및 취소 API")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/confirm")
    @Operation(summary = "결제 승인", description = "매점 주문 또는 결제 대기 영화 예매의 orderId와 서버 금액으로 승인합니다. "
            + "영화 예매는 승인 성공 시에만 확정하며 응답의 reservationId로 예매를 식별합니다. "
            + "재전송할 때 같은 본문과 Idempotency-Key를 사용합니다. 재전송 응답은 해당 작업 당시 결과입니다.")
    public ApiResponse<PaymentCommandResponse> confirm(
            @AuthenticationPrincipal UserDetails user,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return success(paymentService.confirm(user.getUsername(), request, key), SuccessCode.UPDATE_SUCCESS);
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "결제와 작업 이력 조회", description = "매점 주문·영화 예매 생성 시 받은 paymentId로 본인의 현재 결제와 작업 이력을 DB에서 조회합니다. "
            + "영화 결제에는 reservationId, 현재 reservationStatus, confirmedAt을 함께 반환합니다. "
            + "조회는 PG 승인·복구를 실행하지 않으며 결과 불명 승인 복구는 같은 본문과 멱등키로 승인 API를 재요청합니다.")
    public ApiResponse<PaymentResponse> getPayment(
            @AuthenticationPrincipal UserDetails user, @PathVariable("paymentId") Long paymentId) {
        return success(paymentService.getPayment(user.getUsername(), paymentId), SuccessCode.SELECT_SUCCESS);
    }

    @PostMapping("/{paymentId}/cancellations")
    @Operation(summary = "결제 전체·부분 취소", description = "cancelAmount를 생략하면 남은 금액 전체를 취소합니다. 영화 예매는 전체 취소만 지원합니다.")
    public ApiResponse<PaymentCommandResponse> cancel(
            @AuthenticationPrincipal UserDetails user, @PathVariable("paymentId") Long paymentId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentCancelRequest request) {
        return success(paymentService.cancel(user.getUsername(), paymentId, request, key), SuccessCode.UPDATE_SUCCESS);
    }

    private <T> ApiResponse<T> success(T result, SuccessCode code) {
        return new ApiResponse<>(result, code.getStatus(), code.getMessage());
    }
}
