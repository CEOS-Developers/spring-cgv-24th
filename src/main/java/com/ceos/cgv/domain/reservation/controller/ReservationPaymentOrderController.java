package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.payment.dto.request.PaymentOrderCreateRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.service.result.PaymentOrderCreationResult;
import com.ceos.cgv.domain.reservation.service.payment.ReservationPaymentApplicationService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import com.ceos.cgv.global.security.principal.CgvUserDetails;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "payment-controller")
public class ReservationPaymentOrderController {
    private final ReservationPaymentApplicationService reservationPayments;

    @PostMapping("/payment-orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @AuthenticationPrincipal CgvUserDetails user,
            @Valid @RequestBody PaymentOrderCreateRequest request) {
        PaymentOrderCreationResult creation =
                reservationPayments.createOrder(user.userId(), request);
        PaymentOrderResponse result = creation.response();
        if (!creation.created()) return ResponseEntity.ok(ApiResponse.success(result));
        return ResponseEntity.created(URI.create("/api/v1/payment-orders/" + result.orderId()))
                .body(ApiResponse.created(result));
    }
}
