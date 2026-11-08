package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.domain.payment.entity.PaymentCancelStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

// 회원에게는 우리 DB 값만 보여준다. 조회할 때마다 토스를 부르면 상점 전체가 함께 쓰는 호출 한도(분당 100건)를
// 회원의 새로고침이 써 버린다. 카드 번호·할부·영수증 같은 결제수단 상세는 토스가 관리하는 정보라 저장하지 않는다.
public record PaymentResponse(
        String orderId,
        PaymentStatus status,
        String statusName,
        int amount,
        int canceledAmount,
        int balanceAmount,
        String method,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt,
        String failureCode,
        String failureMessage,
        List<CancelSummary> cancels
) {
    // id는 결과 불명 취소를 다시 보낼 때 쓴다.
    public record CancelSummary(
            Long id,
            int amount,
            String reason,
            PaymentCancelStatus status,
            String statusName,
            LocalDateTime requestedAt,
            LocalDateTime canceledAt,
            String failureCode,
            String failureMessage
    ) {
        public static CancelSummary from(PaymentCancel cancel) {
            return new CancelSummary(
                    cancel.getId(),
                    cancel.getAmount(),
                    cancel.getReason(),
                    cancel.getStatus(),
                    cancel.getStatus().getDisplayName(),
                    cancel.getRequestedAt(),
                    cancel.getCanceledAt(),
                    cancel.getFailureCode(),
                    cancel.getFailureMessage()
            );
        }
    }

    // 취소 이력은 호출자가 이 결제의 것만 골라 넘긴다. 결제 여러 건의 이력을 한 쿼리로 읽기 위해서다.
    public static PaymentResponse of(Payment payment, List<PaymentCancel> cancels) {
        return new PaymentResponse(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getStatus().getDisplayName(),
                payment.getAmount(),
                payment.getCanceledAmount(),
                payment.getBalanceAmount(),
                payment.getMethod(),
                payment.getCreatedAt(),
                payment.getApprovedAt(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                cancels.stream().map(CancelSummary::from).toList()
        );
    }
}
