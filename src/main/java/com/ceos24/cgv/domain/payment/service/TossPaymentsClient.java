package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;

public interface TossPaymentsClient {

    default TossPaymentResponse confirm(TossConfirmRequest request) {
        return confirm(request, request != null ? request.orderId() : null);
    }

    TossPaymentResponse confirm(TossConfirmRequest request, String idempotencyKey);

    TossPaymentResponse getPayment(String paymentKey);

    default TossPaymentResponse cancel(String paymentKey, TossCancelRequest request) {
        return cancel(paymentKey, request, null);
    }

    TossPaymentResponse cancel(String paymentKey, TossCancelRequest request, String idempotencyKey);
}
