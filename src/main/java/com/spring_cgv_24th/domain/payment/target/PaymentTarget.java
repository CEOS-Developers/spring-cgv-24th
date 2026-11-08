package com.spring_cgv_24th.domain.payment.target;

import com.spring_cgv_24th.domain.member.entity.Member;
import java.time.LocalDateTime;

public interface PaymentTarget {
    Long getId();
    Member getMember();
    String getPaymentOrderId();
    LocalDateTime getExpiresAt();
    void beginPayment(LocalDateTime now);
    void completePayment();
    void failPayment();
    void validatePaymentCancellation(LocalDateTime now);
}
