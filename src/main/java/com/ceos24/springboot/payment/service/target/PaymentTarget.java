package com.ceos24.springboot.payment.service.target;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.dto.PaymentConfirmResponse;

import java.time.LocalDateTime;

// 잠금 조회한 구매 대상을 현재 트랜잭션 안에서만 사용한다.
public interface PaymentTarget {
    PaymentTargetType type();
    Long id();
    Long amount();
    boolean isCompleted();
    boolean isHeld();
    ErrorCode expiredError();
    void beginPayment(LocalDateTime now);
    void completePayment();
    void failPayment(LocalDateTime now);
    void validatePaid();
    // 신규 환불 접수에만 호출한다. 결과 반영 및 복구에는 기한 검증을 적용하지 않는다.
    void validateRefund(LocalDateTime now);
    void refund();
    PaymentConfirmResponse response(Payment payment);
}
