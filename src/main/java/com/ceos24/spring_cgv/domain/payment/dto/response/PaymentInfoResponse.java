package com.ceos24.spring_cgv.domain.payment.dto.response;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "완료된 결제 단건 조회 응답")
public record PaymentInfoResponse(

        @Schema(description = "결제 ID", example = "1")
        Long paymentId,

        @Schema(description = "주문 번호", example = "cgv_8f14e45f")
        String orderId,

        @Schema(description = "주문명", example = "아바타: 물의 길 2석")
        String orderName,

        @Schema(description = "결제 총 금액", example = "28000")
        Integer amount,

        @Schema(description = "결제 수단", example = "카드")
        String method,

        @Schema(description = "결제 상태", example = "DONE")
        PaymentStatus status,

        @Schema(description = "토스가 발급한 결제 키", example = "tgen_20260116142345abcdef")
        String paymentKey,

        @Schema(description = "결제 요청 시각", example = "2026-10-16T14:23:10")
        LocalDateTime requestedAt,

        @Schema(description = "결제 승인 시각", example = "2026-10-16T14:23:45")
        LocalDateTime approvedAt,

        @Schema(description = "이 결제로 확정된 예매 ID", example = "1")
        Long reservationId
) {
    // 승인(DONE) 상태의 결제만 받는다는 전제로 작성했다.
    // method/paymentKey/approvedAt/reservation은 승인 시점에 채워지므로,
    // 다른 상태의 결제를 넘기면 method에서 NPE가 발생한다.
    public static PaymentInfoResponse from(Payment payment) {

        return PaymentInfoResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .orderName(payment.getOrderName())
                .amount(payment.getTotalAmount())
                .method(payment.getMethod().getDescription())
                .status(payment.getStatus())
                .paymentKey(payment.getPaymentKey())
                .requestedAt(payment.getRequestedAt())
                .approvedAt(payment.getApprovedAt())
                .reservationId(payment.getReservation().getId())
                .build();
    }
}
