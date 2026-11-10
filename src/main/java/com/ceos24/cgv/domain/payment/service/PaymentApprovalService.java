package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.TossConfirmResponse;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

        TossConfirmResponse response;
        try {
            response = tossPaymentClient.confirm(request);
        } catch (RestClientException e) {
            // HTTP 오류만으로 PG의 최종 상태를 단정할 수 없다.
            paymentTransactionService.markApprovalUnknown(request);
            if (e instanceof RestClientResponseException responseError
                    && responseError.getStatusCode().is4xxClientError()) {
                throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_REJECTED);
            }
            throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_UNRESOLVED);
        }

        return paymentTransactionService.completeApproval(request, response);
    }

    //복구 메서드 추가
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TossConfirmResponse recoverApproval(
            Long userId,
            PaymentConfirmRequest request
    ) {
        // 로그인 사용자, 주문, 금액, 결제 키와 APPROVING 상태를 검증한다.
        paymentTransactionService.validateApprovalRecovery(userId, request);

        TossConfirmResponse response;
        try {
            response = tossPaymentClient.getPayment(request.paymentKey());
        } catch (RestClientException e) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_UNRESOLVED);
        }

        if ("DONE".equals(response.status())) {
            return paymentTransactionService.completeApproval(request, response);
        }
        if ("ABORTED".equals(response.status())
                || "EXPIRED".equals(response.status())) {
            paymentTransactionService.completeFailedApproval(request, response);
            throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_REJECTED);
        }
        throw new PaymentException(PaymentErrorCode.PAYMENT_APPROVAL_UNRESOLVED);
    }
}
