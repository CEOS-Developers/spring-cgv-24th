package com.ceos24.cgv.domain.payment.dto.response;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentOperation;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentOperationType;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record PaymentResponse(
        Long paymentId, Long purchaseId, String orderId, String paymentKey, PaymentStatus status,
        long totalAmount, long canceledAmount, long remainingAmount, String method,
        Instant approvedAt, List<Operation> operations,
        PaymentTargetType targetType, Long targetId, Long reservationId,
        @Schema(description = "연결된 예매의 현재 상태. 매점 결제는 null입니다.")
        ReservationStatus reservationStatus,
        @Schema(description = "연결된 예매의 확정 시각. 확정 이력이 없거나 매점 결제면 null입니다.")
        LocalDateTime confirmedAt
) {
    public static PaymentResponse from(Payment payment, List<PaymentOperation> operations) {
        Reservation reservation = payment.getReservation();
        return new PaymentResponse(payment.getId(), payment.getPurchase() == null ? null : payment.getPurchase().getId(), payment.getOrderId(),
                payment.getPaymentKey(), payment.getStatus(), payment.getTotalAmount(), payment.getCanceledAmount(),
                payment.getRemainingAmount(), payment.getMethod(), payment.getApprovedAt(),
                operations.stream().map(Operation::from).toList(), payment.getTargetType(), payment.getTargetId(),
                reservation == null ? null : reservation.getId(),
                reservation == null ? null : reservation.getStatus(),
                reservation == null ? null : reservation.getConfirmedAt());
    }

    public record Operation(Long operationId, PaymentOperationType type, PaymentOperationStatus status,
                            long amount, boolean fullCancellation, String cancelReason,
                            String errorCode, String errorMessage, Instant requestedAt, Instant completedAt) {
        static Operation from(PaymentOperation operation) {
            return new Operation(operation.getId(), operation.getType(), operation.getStatus(), operation.getAmount(),
                    operation.isFullCancellation(), operation.getCancelReason(), operation.getErrorCode(),
                    operation.getErrorMessage(), operation.getRequestedAt(), operation.getCompletedAt());
        }
    }
}
