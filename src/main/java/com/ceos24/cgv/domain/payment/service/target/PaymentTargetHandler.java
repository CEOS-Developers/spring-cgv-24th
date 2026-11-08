package com.ceos24.cgv.domain.payment.service.target;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;

// 모든 메서드는 PaymentTransactionService의 DB 트랜잭션 안에서 실행한다. 외부 PG 통신은 하지 않는다.
public interface PaymentTargetHandler {
    PaymentTargetType type();
    void lockTarget(Long targetId);
    boolean isPayable(Payment payment);
    default void validateCancellation(Payment payment, long amount) { }
    void afterApproval(Payment payment);
    void afterPartialCancellation(Payment payment);
    void afterFullCancellation(Payment payment);
    void afterConfirmationRejected(Payment payment);
    void afterExpiration(Payment payment);
}
