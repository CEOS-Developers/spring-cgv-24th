package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "결제 관련 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "결제 준비", description = "주문에 대한 결제 준비(READY) 데이터를 생성하고 토스 연동용 주문번호를 발급합니다.")
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> preparePayment(
            @RequestBody PaymentPrepareRequest request) {
        PaymentPrepareResponse response = paymentService.preparePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "결제 최종 승인", description = "주문 및 결제 금액을 검증한 후 토스페이먼츠에 결제 승인을 요청합니다.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(@RequestBody PaymentConfirmRequest request) {
        PaymentResponse response = paymentService.confirmPayment(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 단건 조회", description = "결제 키(paymentKey)를 통해 자체 DB에서 결제 정보 및 품목별 취소 이력을 조회합니다.")
    @GetMapping("/{paymentKey}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable String paymentKey) {
        PaymentResponse response = paymentService.getPayment(paymentKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 취소 (전체 / 품목별 부분 취소)", description = "토스 결제를 전체 또는 품목 단위로 부분 취소하고 남은 잔액을 차감합니다.")
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @PathVariable String paymentKey, @RequestBody PaymentCancelRequest request) {
        PaymentResponse response = paymentService.cancelPayment(paymentKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
