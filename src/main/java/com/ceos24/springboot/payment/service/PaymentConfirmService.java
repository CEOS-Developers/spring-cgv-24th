package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class PaymentConfirmService {

    private final PaymentConfirmTransactionService transactionService;
    private final TossPaymentClient tossPaymentClient;

    // 외부 API 호출을 포함하므로 여기에는 @Transactional을 붙이지 않는다.
    public PaymentConfirmResponse confirm(
            Long userId,
            PaymentConfirmRequest request
    ) {
        request.validate();

        var context = transactionService.begin(userId, request);

        if (context.completed() != null) {
            return context.completed();
        }

        TossPaymentConfirmResponse remote;

        try {
            if (context.lookupOnly()) {
                remote = tossPaymentClient.findByPaymentKey(
                        context.paymentKey()
                );
            } else {
                remote = tossPaymentClient.confirm(
                        new TossPaymentConfirmRequest(
                                context.paymentKey(),
                                context.orderId(),
                                context.amount()
                        ),
                        context.idempotencyKey()
                );
            }
        } catch (RestClientException e) {
            // 승인 응답을 못 받았어도 토스에서는 성공했을 수 있다.
            remote = lookupOrNull(context.paymentKey());
        }

        // 이 호출이 반환되면 DB 트랜잭션은 커밋된 상태다.
        PaymentConfirmResponse response =
                transactionService.finish(context, remote);

        if (response.status() == PaymentStatus.DONE) {
            return response;
        }

        if (response.status() == PaymentStatus.FAILED) {
            throw new GlobalException(
                    ErrorCode.PAYMENT_APPROVAL_FAILED
            );
        }

        throw new GlobalException(ErrorCode.PAYMENT_RESULT_UNKNOWN);
    }

    private TossPaymentConfirmResponse lookupOrNull(String paymentKey) {
        try {
            return tossPaymentClient.findByPaymentKey(paymentKey);
        } catch (RestClientException e) {
            return null;
        }
    }
}