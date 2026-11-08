package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.payment.dto.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareRequest;
import com.ceos24.cgv.domain.payment.dto.PaymentPrepareResponse;
import com.ceos24.cgv.domain.payment.dto.PaymentResponse;
import com.ceos24.cgv.domain.payment.dto.TossWebhookRequest;
import com.ceos24.cgv.domain.payment.service.PaymentService;
import com.ceos24.cgv.domain.payment.service.PaymentWebhookService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.security.ApiUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "결제", description = "결제 관련 API")
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentWebhookService paymentWebhookService;

    @Operation(summary = "결제 준비", description = "스토어 주문 또는 영화 예매에 대한 결제 준비(READY) 데이터를 생성하고 토스 연동용 주문번호를 발급합니다.")
    @PostMapping("/prepare")
    public ResponseEntity<ApiResponse<PaymentPrepareResponse>> preparePayment(
            @AuthenticationPrincipal ApiUserDetails userDetails, @Valid @RequestBody PaymentPrepareRequest request) {
        PaymentPrepareResponse response = paymentService.preparePayment(userDetails.memberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "결제 최종 승인", description = "주문/예매 금액을 검증하고 승인 진행(IN_PROGRESS) 상태를 선점한 후 토스페이먼츠에 결제 승인을 요청합니다.")
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @AuthenticationPrincipal ApiUserDetails userDetails, @Valid @RequestBody PaymentConfirmRequest request) {
        PaymentResponse response = paymentService.confirmPayment(userDetails.memberId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 단건 조회", description = "결제 키(paymentKey)를 통해 자체 DB에서 결제 정보 및 품목/좌석별 취소 이력을 조회합니다.")
    @GetMapping("/{paymentKey}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal ApiUserDetails userDetails, @PathVariable String paymentKey) {
        PaymentResponse response = paymentService.getPayment(userDetails.memberId(), paymentKey);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "결제 취소 (전체 / 품목별·좌석별 부분 취소)", description = "토스 결제를 전체 또는 품목/좌석 단위로 부분 취소하고 남은 잔액을 차감합니다.")
    @PostMapping("/{paymentKey}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal ApiUserDetails userDetails,
            @PathVariable String paymentKey,
            @Valid @RequestBody PaymentCancelRequest request) {
        PaymentResponse response = paymentService.cancelPayment(userDetails.memberId(), paymentKey, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "토스페이먼츠 웹훅 수신",
            description = "토스페이먼츠에서 발송하는 결제 상태 변경(DONE, CANCELED, ABORTED, EXPIRED) 이벤트를 수신하여 동기화합니다.")
    @SecurityRequirements
    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> handleWebhook(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "토스페이먼츠 웹훅 페이로드",
                            required = true,
                            content = @Content(schema = @Schema(implementation = TossWebhookRequest.class)))
                    @RequestBody
                    String rawBody,
            @RequestHeader(value = "tosspayments-webhook-signature", required = false) String signature,
            @RequestHeader(value = "tosspayments-webhook-transmission-time", required = false) String transmissionTime,
            @RequestHeader(value = "X-Webhook-Secret", required = false) String webhookSecret) {
        paymentWebhookService.handleWebhook(rawBody, signature, transmissionTime, webhookSecret);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
