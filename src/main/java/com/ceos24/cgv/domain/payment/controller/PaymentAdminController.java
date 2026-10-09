package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentReconcileResponse;
import com.ceos24.cgv.domain.payment.service.PaymentAdminService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 권한 검사는 SecurityConfig의 /api/admin/** 규칙이 한다.
// 공용 테스트 키라 개발자센터에서 결제 내역을 볼 수 없다. 토스 쪽 상태를 확인하는 수단이 이 API다.
@Tag(name = "결제 관리", description = "관리자 전용 결제 대조")
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class PaymentAdminController {

    private final PaymentAdminService paymentAdminService;

    @Operation(summary = "토스 결제 대조 — 같은 주문의 우리 DB 값과 토스 조회 결과를 나란히")
    @GetMapping("/{orderId}/toss")
    public ApiResponse<PaymentReconcileResponse> reconcile(@PathVariable String orderId) {
        return ApiResponse.success(paymentAdminService.reconcile(orderId));
    }
}
