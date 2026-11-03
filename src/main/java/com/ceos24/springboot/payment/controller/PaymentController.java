package com.ceos24.springboot.payment.controller;

import com.ceos24.springboot.payment.dto.PaymentConfirmRequest;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;
import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
import com.ceos24.springboot.payment.dto.StoreOrderPaymentPrepareRequest;
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
