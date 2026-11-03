package com.ceos24.springboot.payment.controller;

import com.ceos24.springboot.payment.dto.PaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.StoreOrderPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.PaymentDetailResponse;
import com.ceos24.springboot.payment.dto.PaymentHistoryResponse;
import com.ceos24.springboot.payment.service.PaymentQueryService;
import com.ceos24.springboot.payment.service.PaymentCancelService;
import com.ceos24.springboot.payment.dto.PaymentCancelRequest;
import com.ceos24.springboot.payment.dto.PaymentCancelResponse;
import java.util.List;
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
    private final PaymentCancelService paymentCancelService;

    @Operation(summary = "본인 결제 전체·부분 취소", description = "동일한 취소 재시도에는 같은 Idempotency-Key와 Body를 사용합니다. cancelAmount 생략 시 남은 금액 전체를 취소합니다.")
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PaymentCancelResponse> cancel(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestId,
            @RequestBody PaymentCancelRequest request
    ) {
        return ResponseEntity.ok(paymentCancelService.cancel(userDetails.getUserId(), paymentId, requestId, request));
    }

    @Operation(summary = "본인 결제 취소 요청 이력", description = "완료·실패·결과 미확정 요청을 우리 DB에서 조회합니다.")
    @GetMapping("/{paymentId}/cancellations")
    public ResponseEntity<List<PaymentCancelResponse>> getCancellations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long paymentId
    ) {
        return ResponseEntity.ok(paymentQueryService.getCancellations(userDetails.getUserId(), paymentId));
    }

    @Operation(summary = "본인 승인 결제 내역", description = "승인 이력이 있는 결제를 부분·전체 취소 이후에도 조회합니다. PG 호출은 하지 않습니다.")
    @GetMapping
    public ResponseEntity<PaymentHistoryResponse> getCompletedPayments(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(paymentQueryService.getCompletedPayments(userDetails.getUserId(), page, size));
    }

    @Operation(summary = "본인 승인 결제 상세", description = "본인 승인 결제의 현재 상태·원금·취소 금액·잔액을 우리 DB에서 조회합니다.")
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
