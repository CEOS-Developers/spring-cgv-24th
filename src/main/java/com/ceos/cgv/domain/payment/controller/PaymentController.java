package com.ceos.cgv.domain.payment.controller;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.service.PaymentQueryService;
import com.ceos.cgv.domain.payment.service.PaymentWorkflowService;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.global.common.dto.ApiResponse;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import com.ceos.cgv.global.security.principal.CgvUserDetails;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

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

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {
    private final PaymentWorkflowService paymentWorkflowService;
    private final PaymentQueryService queryService;

    @PostMapping("/payment-orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @AuthenticationPrincipal CgvUserDetails user,
            @Valid @RequestBody PaymentOrderCreateRequest request) {
        PaymentOrderCreationResult creation =
                paymentWorkflowService.createOrderResult(user.userId(), request);
        PaymentOrderResponse result = creation.response();
        if (!creation.created()) return ResponseEntity.ok(ApiResponse.success(result));
        return ResponseEntity.created(URI.create("/api/v1/payment-orders/" + result.orderId()))
                .body(ApiResponse.created(result));
    }

    @GetMapping("/payment-orders/{orderId}")
    public ApiResponse<PaymentOrderResponse> order(
            @AuthenticationPrincipal CgvUserDetails user, @PathVariable String orderId) {
        return ApiResponse.success(queryService.order(user.userId(), orderId));
    }

    @PostMapping("/payments/confirm")
    public ResponseEntity<ApiResponse<PaymentOperationResponse>> confirm(
            @AuthenticationPrincipal CgvUserDetails user,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentConfirmRequest request) {
        return response(paymentWorkflowService.confirm(user.userId(), requestKey(key), request));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentResponse> payment(
            @AuthenticationPrincipal CgvUserDetails user, @PathVariable Long paymentId) {
        return ApiResponse.success(queryService.payment(user.userId(), paymentId));
    }

    @PostMapping("/payments/{paymentId}/cancellations")
    public ResponseEntity<ApiResponse<PaymentOperationResponse>> cancel(
            @AuthenticationPrincipal CgvUserDetails user,
            @PathVariable Long paymentId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentCancelRequest request) {
        return response(
                paymentWorkflowService.cancel(user.userId(), paymentId, requestKey(key), request));
    }

    @GetMapping("/payment-operations/{operationId}")
    public ApiResponse<PaymentOperationResponse> operation(
            @AuthenticationPrincipal CgvUserDetails user, @PathVariable Long operationId) {
        return ApiResponse.success(queryService.operation(user.userId(), operationId));
    }

    @PostMapping("/payment-operations/{operationId}/reconcile")
    public ResponseEntity<ApiResponse<PaymentOperationResponse>> reconcile(
            @AuthenticationPrincipal CgvUserDetails user, @PathVariable Long operationId) {
        return response(paymentWorkflowService.reconcile(user.userId(), operationId));
    }

    public static UUID requestKey(String key) {
        try {
            UUID value = UUID.fromString(key);
            if (!value.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
            return value;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    public static ResponseEntity<ApiResponse<PaymentOperationResponse>> response(
            PaymentOperationResponse result) {
        boolean pending =
                result.status() == PaymentOperationStatus.PROCESSING
                        || result.status() == PaymentOperationStatus.UNKNOWN
                        || result.status() == PaymentOperationStatus.COMPENSATING;
        HttpStatus status = pending ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .location(URI.create(result.operationUrl()))
                .body(
                        new ApiResponse<>(
                                status.value(),
                                pending ? "결제 처리 결과를 확인 중입니다." : "결제 작업 조회에 성공하였습니다.",
                                result));
    }
}
