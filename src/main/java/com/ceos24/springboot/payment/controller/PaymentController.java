package com.ceos24.springboot.payment.controller;

import com.ceos24.springboot.payment.dto.PaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.StoreOrderPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.PaymentDetailResponse;
import com.ceos24.springboot.payment.dto.PaymentHistoryResponse;
import com.ceos24.springboot.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import com.ceos24.springboot.payment.service.PaymentConfirmService;
import com.ceos24.springboot.payment.service.PaymentService;
import com.ceos24.springboot.user.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentConfirmService paymentConfirmService;
    private final PaymentQueryService paymentQueryService;

    @Operation(summary = "본인 완료 결제 목록", description = "우리 DB의 DONE 결제를 승인 시각 내림차순으로 조회합니다. PG 호출은 하지 않습니다.")
    @GetMapping
    public ResponseEntity<PaymentHistoryResponse> getCompletedPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(paymentQueryService.getCompletedPayments(userDetails.getUserId(), page, size));
    }

    @Operation(summary = "본인 완료 결제 상세", description = "paymentId로 본인의 DONE 결제만 조회합니다. PG 호출은 하지 않습니다.")
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getCompletedPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(paymentQueryService.getCompletedPayment(userDetails.getUserId(), paymentId));
    }

    @PostMapping("/store-orders/prepare")
    public ResponseEntity<PaymentPrepareResponse> prepareStoreOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody StoreOrderPaymentPrepareRequest request
    ) {
        return ResponseEntity.ok(paymentService.prepareStoreOrder(userDetails.getUserId(), request));
    }

    @PostMapping("/reservations/prepare")
    public ResponseEntity<PaymentPrepareResponse> prepareReservation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody ReservationPaymentPrepareRequest request
    ) {
        return ResponseEntity.ok(
                paymentService.prepareReservation(
                        userDetails.getUserId(),
                        request
                )
        );
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentConfirmResponse> confirm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PaymentConfirmRequest request
    ) {
        return ResponseEntity.ok(
                paymentConfirmService.confirm(
                        userDetails.getUserId(),
                        request
                )
        );
    }
}
