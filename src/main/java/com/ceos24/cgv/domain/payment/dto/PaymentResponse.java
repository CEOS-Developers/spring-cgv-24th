package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;

import java.time.LocalDateTime;

// 회원에게는 우리 DB 값만 보여준다. 조회할 때마다 토스를 부르면 상점 전체가 함께 쓰는 호출 한도(분당 100건)를
// 회원의 새로고침이 써 버린다. 카드 번호·할부·영수증 같은 결제수단 상세는 토스가 관리하는 정보라 저장하지 않는다.
public record PaymentResponse(
        String orderId,
        PaymentStatus status,
        String statusName,
        int amount,
        String method,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        String failureCode,
        String failureMessage
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getStatus().getDisplayName(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getCreatedAt(),
                payment.getApprovedAt(),
                payment.getFailureCode(),
                payment.getFailureMessage()
        );
    }
}
