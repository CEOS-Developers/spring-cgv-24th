package com.ceos24.cgv.domain.payment.dto;

// 결과를 몰랐던 승인 결제 한 건을 토스 조회로 정리한 결과다. 예매·매점이 함께 쓴다.
// UNRESOLVED는 실패가 아니다. 결제는 승인 중으로 남고 다음 정리에서 다시 묻는다.
public record PaymentRecoveryResponse(
        String orderId,
        Outcome outcome,
        String outcomeName,
        String detail
) {
    public static PaymentRecoveryResponse of(String orderId, Outcome outcome, String detail) {
        return new PaymentRecoveryResponse(orderId, outcome, outcome.getDisplayName(), detail);
    }

    public enum Outcome {
        APPROVED("승인 확정"),
        REFUNDED("자원을 잃어 환불"),
        REFUND_PENDING("환불 결과 불명, 재시도 예정"),
        REFUND_REJECTED("환불 거절, 수동 확인 필요"),
        FAILED("토스 미승인, 실패 확정"),
        UNRESOLVED("판단 보류");

        private final String displayName;

        Outcome(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }
}
