package com.ceos24.springboot.payment.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentCancellationStatus;
import com.ceos24.springboot.payment.dto.*;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.payment.toss.TossPaymentErrorPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class PaymentCancelService {
    private final PaymentCancelTransactionService transactionService;
    private final TossPaymentClient toss;

    // 승인과 동일하게 PG 호출 중에는 DB 트랜잭션과 락을 유지하지 않는다.
    public PaymentCancelResponse cancel(Long userId, Long paymentId, String requestId, PaymentCancelRequest request) {
        if (request == null) { throw new GlobalException(ErrorCode.INVALID_PAYMENT_CANCEL_REQUEST); }
        request.validate(requestId);
        var context = transactionService.begin(userId, paymentId, requestId, request);
        if (context.completed() != null) { return requireDone(context.completed()); }

        TossPaymentCancelResponse remote;
        boolean rejected = false;
        try {
            remote = context.lookupOnly() ? toss.findForCancellation(context.paymentKey())
                    : toss.cancel(context.paymentKey(), context.request(), context.pgIdempotencyKey());
        } catch (RestClientResponseException e) {
            rejected = !context.lookupOnly() && TossPaymentErrorPolicy.isDefinitiveRejection(e);
            remote = null;
        } catch (RestClientException e) {
            remote = null;
        }
        // 트랜잭션 커밋 후 예외를 반환하여 UNKNOWN/FAILED 기록이 롤백되지 않게 한다.
        return requireDone(transactionService.finish(context, remote, rejected));
    }

    private PaymentCancelResponse requireDone(PaymentCancelResponse response) {
        if (response.cancelStatus() == PaymentCancellationStatus.DONE) { return response; }
        if (response.cancelStatus() == PaymentCancellationStatus.FAILED) {
            throw new GlobalException(ErrorCode.PAYMENT_CANCEL_FAILED);
        }
        throw new GlobalException(ErrorCode.PAYMENT_CANCEL_RESULT_UNKNOWN);
    }
}
