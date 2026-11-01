package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.TossPaymentResponse;

public interface TossPaymentsClient {

    TossPaymentResponse confirm(TossConfirmRequest request);

    TossPaymentResponse getPayment(String paymentKey);

    TossPaymentResponse cancel(String paymentKey, TossCancelRequest request);
}
