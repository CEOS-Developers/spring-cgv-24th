package com.ceos24.cgv.domain.payment.repository;

import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;

// 잠금을 획득하기 전에 Payment 엔티티를 읽어 오래된 상태를 캐시하지 않도록 식별자만 조회한다.
public record PaymentTargetReference(PaymentTargetType type, Long purchaseId, Long reservationId, Long payerId) {
    public Long targetId() {
        return switch (type) {
            case PURCHASE -> purchaseId;
            case RESERVATION -> reservationId;
        };
    }
}
