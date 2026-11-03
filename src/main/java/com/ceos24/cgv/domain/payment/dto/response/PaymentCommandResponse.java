package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentOperation;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationType;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PaymentCommandResponse(
        Long paymentId, String orderId, String paymentKey, Long operationId,
        PaymentOperationType operationType, long amount, PaymentStatus paymentStatus,
        long totalAmount, long canceledAmount, long remainingAmount, Instant completedAt,
        PaymentTargetType targetType, Long targetId
) {
    // 작업 당시 응답은 유지하면서 불변인 결제 대상 ID를 명시한다.
    @JsonProperty("reservationId")
    @Schema(description = "영화 예매 결제의 예매 ID. 매점 결제는 null입니다.")
    public Long reservationId() {
        return targetType == PaymentTargetType.RESERVATION ? targetId : null;
    }

    public static PaymentCommandResponse from(Payment payment, PaymentOperation operation) {
        long canceled = operation.getResultCanceledAmount();
        return new PaymentCommandResponse(payment.getId(), payment.getOrderId(),
                operation.getPaymentKey(), operation.getId(), operation.getType(), operation.getAmount(),
                operation.getResultPaymentStatus(), payment.getTotalAmount(), canceled,
                payment.getTotalAmount() - canceled, operation.getCompletedAt(), payment.getTargetType(), payment.getTargetId());
    }
}
