package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.payment.dto.response.PaymentDetailResponse;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.exception.PaymentErrorCode;
import com.ceos24.cgv.domain.payment.exception.PaymentException;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    //주문 번호로 로그인한 사용자의 결제 정보를 조회
    //예매 생성시 발급한 주문 번호로 !!
    public PaymentDetailResponse getPayment(Long userId, String orderId) {

        // 결제 상세 화면에서는 DB에 저장된 상태와 금액을 사용
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND)
                );

        // 다른 사용자의 주문번호를 알고 있더라도 결제 정보를 볼 수 없게 처리함
        Long ownerId = payment.getReservation().getUser().getId();
        if (!Objects.equals(ownerId, userId)) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ACCESS_DENIED);
        }

        return PaymentDetailResponse.from(payment);
    }
}