package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseOrderResponse;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.domain.store.service.PurchasePaymentService;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "매점 구매", description = "매점 주문 / 결제 / 구매 내역")
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;
    private final PurchasePaymentService purchasePaymentService;

    @Operation(summary = "매점 주문 — 주문과 결제위젯에 넘길 orderId·금액 발급. 재고는 아직 차감하지 않음")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseOrderResponse> createOrder(@AuthenticationPrincipal AuthUser authUser,
                                                          @Valid @RequestBody PurchaseCreateRequest req) {
        return ApiResponse.success(purchaseService.createOrder(authUser.userId(), req));
    }

    @Operation(summary = "결제 승인 — 재고 차감 후 토스 승인. 성공 시 구매 확정, 거절 시 재고 복구")
    @PostMapping("/{id}/payments/confirm")
    public ApiResponse<PurchaseResponse> confirmPayment(@AuthenticationPrincipal AuthUser authUser,
                                                        @PathVariable Long id,
                                                        @Valid @RequestBody PaymentConfirmRequest req) {
        return ApiResponse.success(purchasePaymentService.confirm(id, authUser.userId(), req));
    }

    @Operation(summary = "구매 결제 조회")
    @GetMapping("/{id}/payment")
    public ApiResponse<PaymentResponse> payment(@AuthenticationPrincipal AuthUser authUser,
                                                @PathVariable Long id) {
        return ApiResponse.success(purchaseService.findPayment(id, authUser.userId()));
    }

    @Operation(summary = "내 구매 내역 (최근 구매 순)")
    @GetMapping
    public ApiResponse<List<PurchaseResponse>> history(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(purchaseService.findHistory(authUser.userId()));
    }
}
