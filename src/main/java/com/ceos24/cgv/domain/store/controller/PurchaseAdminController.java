package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentRecoveryResponse;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.domain.store.service.PurchasePaymentRecoveryService;
import com.ceos24.cgv.domain.store.service.PurchaseRefundService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

// 권한 검사는 SecurityConfig의 /api/admin/** 규칙이 한다.
// 결제가 곧 제조 접수라 회원은 결제한 매점 구매를 취소하지 못한다. 매장이 제공하지 못한 수량만 환불한다.
@Tag(name = "매점 환불 (관리자)", description = "매장이 제공하지 못한 수량의 환불, 결과 불명 결제 정리")
@RestController
@RequestMapping("/api/admin/purchases")
@RequiredArgsConstructor
public class PurchaseAdminController {

    private final PurchaseRefundService purchaseRefundService;
    private final PurchasePaymentRecoveryService purchasePaymentRecoveryService;

    @Operation(summary = "전체 환불 — 남은 수량 전부를 토스로 환불. 재고는 되돌리지 않음")
    @DeleteMapping("/{id}")
    public ApiResponse<PurchaseResponse> refundAll(@PathVariable Long id) {
        return ApiResponse.success(purchaseRefundService.refundAll(id));
    }

    @Operation(summary = "부분 환불 — 상품별 수량만큼 구매 시점 단가로 환불. 재고는 되돌리지 않음")
    @PostMapping("/{id}/cancellations")
    public ApiResponse<PurchaseResponse> refundItems(@PathVariable Long id,
                                                     @Valid @RequestBody PurchaseRefundRequest req) {
        return ApiResponse.success(purchaseRefundService.refundItems(id, req));
    }

    @Operation(summary = "환불 재시도 — 결과를 모르는 환불을 같은 멱등키로 다시 보냄. 요청 후 14일까지")
    @PostMapping("/{id}/cancellations/{cancelId}/retry")
    public ApiResponse<PurchaseResponse> retry(@PathVariable Long id, @PathVariable Long cancelId) {
        return ApiResponse.success(purchaseRefundService.retry(id, cancelId));
    }

    // 스케줄러가 1분마다 하는 정리를 한 건만 바로 돌린다. 승인 중으로 묶인 재고를 다음 회차 전에 풀거나 확정한다.
    @Operation(summary = "결과 불명 매점 결제 정리 — 토스 조회로 확정 / 실패(재고 복구)")
    @PostMapping("/payments/{orderId}/recovery")
    public ApiResponse<PaymentRecoveryResponse> recover(@PathVariable String orderId) {
        return ApiResponse.success(purchasePaymentRecoveryService.recover(orderId));
    }
}
