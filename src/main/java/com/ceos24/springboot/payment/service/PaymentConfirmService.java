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
            // 추가 네트워크 대기 없이 UNKNOWN을 커밋하고 지연 복구한다.
            remote = null;
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

}
