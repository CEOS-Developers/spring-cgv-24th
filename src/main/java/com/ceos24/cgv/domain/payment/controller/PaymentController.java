package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentPartialCancelRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentDetailResponse;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import com.ceos24.cgv.domain.payment.service.PaymentApprovalService;
import com.ceos24.cgv.domain.payment.service.PaymentCancellationService;
import com.ceos24.cgv.domain.payment.service.PaymentQueryService;
import com.ceos24.cgv.domain.reservation.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "결제 API")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentApprovalService paymentApprovalService;

    private final PaymentQueryService paymentQueryService;

    private final PaymentCancellationService paymentCancellationService;

    @PostMapping("/confirm")
    @Operation(
            summary = "영화 예매 결제 승인",
            description = "주문 정보와 금액을 검증한 뒤 토스페이먼츠 결제를 승인합니다."
    )

    public ApiResponse<TossConfirmResponse> confirmPayment(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        TossConfirmResponse response =
                paymentApprovalService.confirm(principal.getUserId(), request);

        return ApiResponse.onSuccess("결제 승인에 성공했습니다.", response);
    }

    //결제 조회


    @GetMapping("/{orderId}")
    @Operation(summary = "내 결제 상세 조회")
    public ApiResponse<PaymentDetailResponse> getPayment(
            @Parameter(description = "예매 생성 시 발급된 주문번호")
            @PathVariable("orderId") String orderId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal
    ) {
        PaymentDetailResponse response =
                paymentQueryService.getPayment(principal.getUserId(), orderId);

        return ApiResponse.onSuccess("결제 정보를 조회했습니다.", response);
    }

    //결제 취소
    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "영화 예매 결제 전체 취소")
    public ApiResponse<PaymentDetailResponse> cancelFull(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody PaymentCancelRequest request
    ) {
        PaymentDetailResponse response = paymentCancellationService.cancelFull(
                principal.getUserId(), orderId, request.cancelReason()
        );

        return ApiResponse.onSuccess("결제 전체 취소에 성공했습니다.", response);
    }

    @PostMapping("/{orderId}/cancel/partial")
    @Operation(summary = "선택한 예매 좌석 결제 취소")
    public ApiResponse<PaymentDetailResponse> cancelPartial(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable("orderId") String orderId,
            @Valid @RequestBody PaymentPartialCancelRequest request
    ) {
        PaymentDetailResponse response = paymentCancellationService.cancelPartial(
                principal.getUserId(), orderId, request
        );

        return ApiResponse.onSuccess("선택한 좌석의 결제를 취소했습니다.", response);
    }

    /**
     * 승인 처리 중 멈춘 결제의 실제 토스 결과를 조회하고,
     * 승인 완료 또는 실패가 확인되면 내부 결제 상태를 복구한다.
     */
    @PostMapping("/confirm/recover")
    @Operation(
            summary = "결제 승인 결과 복구",
            description = "APPROVING/UNKNOWN 상태의 결제를 토스에서 조회하고, "
                    + "DONE이면 결제와 예매를 확정하고 ABORTED/EXPIRED이면 결제를 FAILED로 전환합니다."
    )
    public ApiResponse<TossConfirmResponse> recoverApproval(
            @Parameter(hidden = true)
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody PaymentConfirmRequest request
    ) {
        TossConfirmResponse response =
                paymentApprovalService.recoverApproval(
                        principal.getUserId(),
                        request
                );

        return ApiResponse.onSuccess(
                "결제 승인 결과를 복구했습니다.",
                response
        );
    }
}
