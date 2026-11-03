package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
//승인 준비 -> Toss 호출 -> 결과 저장으로 호출함 . 서로 다른 객체 ..
public class PaymentApprovalService {

    private final PaymentTransactionService paymentTransactionService;
    private final TossPaymentClient tossPaymentClient;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TossConfirmResponse confirm(Long userId, PaymentConfirmRequest request) {
        boolean shouldApprove =
                paymentTransactionService.prepareApproval(userId, request);

        if (!shouldApprove) {
            return paymentTransactionService.getApprovedResult(userId, request);
        }

        TossConfirmResponse response = tossPaymentClient.confirm(request);

        return paymentTransactionService.completeApproval(request, response);
    }
}