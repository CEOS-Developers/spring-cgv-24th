package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.dto.request.PaymentPartialCancelRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentDetailResponse;
import com.ceos24.cgv.domain.payment.dto.response.TossCancelResponse;
import com.ceos24.cgv.domain.payment.service.command.FullCancellationCommand;
import com.ceos24.cgv.domain.payment.service.command.PartialCancellationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCancellationService {

    private final PaymentTransactionService paymentTransactionService;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentQueryService paymentQueryService;

    // 전체 취소 메서드
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentDetailResponse cancelFull(
            Long userId,
            String orderId,
            String reason
    ) {
        //결제 소유자와 취소 가능 여부 검증, 취소 요청 정보를 준비
        FullCancellationCommand command =
                paymentTransactionService.prepareFullCancellation(userId, orderId, reason);

        //동일 취소 요청의 중복 처리를 방지하기 위해 멱등성 키를 함께 전달함.
        TossCancelResponse response = tossPaymentClient.cancelFull(
                command.paymentKey(),
                command.reason(),
                command.idempotencyKey()
        );

        //취소 응답을 바탕으로 결제 및 취소 내역을 갱신함.
        return paymentTransactionService.completeFullCancellation(
                orderId, command.cancellationId(), response
        );
    }


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentDetailResponse cancelPartial(
            Long userId,
            String orderId,
            PaymentPartialCancelRequest request
    ) {
        PartialCancellationCommand command =
                paymentTransactionService.preparePartialCancellation(userId, orderId, request);

        if (command.alreadyCompleted()) {
            return paymentQueryService.getPayment(userId, orderId);
        }

        TossCancelResponse response = tossPaymentClient.cancelPartial(
                command.paymentKey(),
                command.amount(),
                command.reason(),
                command.idempotencyKey()
        );

        return paymentTransactionService.completePartialCancellation(
                orderId, command.cancellationId(), response
        );
    }
}