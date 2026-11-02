package com.ceos24.springboot.payment.controller;

import com.ceos24.springboot.payment.dto.PaymentPrepareResponse;
import com.ceos24.springboot.payment.dto.ReservationPaymentPrepareRequest;
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
}