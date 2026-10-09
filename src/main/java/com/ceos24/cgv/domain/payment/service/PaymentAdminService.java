package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.PaymentReconcileResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 토스 조회를 사이에 두므로 @Transactional을 붙이지 않는다(readOnly 포함). 붙이면 토스 응답을 기다리는
// 최대 1분 동안 DB 커넥션을 쥔다. 우리 결제는 저장소 호출이 짧은 트랜잭션으로 읽고 끝낸다.
@Service
@RequiredArgsConstructor
public class PaymentAdminService {

    private final PaymentRepository paymentRepository;
    private final TossPaymentsClient tossPaymentsClient;

    // 우리 주문이 아닌 orderId는 토스에 묻지 않는다. 관리자 API가 아무 주문이나 조회하는 통로가 되지 않게 한다.
    public PaymentReconcileResponse reconcile(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        return PaymentReconcileResponse.of(payment, tossPaymentsClient.findByOrderId(orderId));
    }
}
