package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.payment.entity.PaymentOrder;
import com.ceos.cgv.domain.payment.entity.PaymentOrderSeat;
import com.ceos.cgv.domain.payment.enums.PaymentTargetType;
import com.ceos.cgv.domain.payment.service.target.PaymentTarget;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReservationPaymentTarget implements PaymentTarget {
    private final ReservationPaymentService reservations;
    private final Clock paymentClock;

    @Override
    public PaymentTargetType targetType() {
        return PaymentTargetType.RESERVATION;
    }

    @Override
    public void beginApproval(PaymentOrder order, Long userId) {
        reservations.startPayment(order.getReservationSnapshot().getReservationId(), userId);
    }

    @Override
    public void completeApproval(PaymentOrder order, Long userId) {
        reservations.confirmPayment(order.getReservationSnapshot().getReservationId(), userId);
    }

    @Override
    public void releaseFailedApproval(PaymentOrder order, Long userId) {
        reservations.releaseFailedPayment(order.getReservationSnapshot().getReservationId(), userId);
    }

    @Override
    public List<Long> selectCancellationItems(PaymentOrder order, List<Long> requestedItemIds) {
        List<Long> active =
                order.getReservationSnapshot().getSeats().stream()
                        .filter(seat -> seat.getCanceledAt() == null)
                        .map(PaymentOrderSeat::getReservedSeatId)
                        .sorted()
                        .toList();
        // 남은 좌석이 없는 전체 취소는 이미 끝난 취소로 처리한다.
        if (requestedItemIds == null && active.isEmpty()) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        List<Long> requested =
                requestedItemIds == null ? active : requestedItemIds.stream().sorted().toList();
        if (requested.isEmpty() || !active.containsAll(requested)) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        return requested;
    }

    @Override
    public long cancellationAmount(PaymentOrder order, Long userId, List<Long> itemIds) {
        var orderSnapshot = order.getReservationSnapshot();
        PaymentReservationSnapshot reservation =
                reservations.lockOwned(orderSnapshot.getReservationId(), userId);
        if (reservation.status() != ReservationStatus.RESERVED) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CONFLICT);
        }
        List<Long> reservationActive =
                reservation.seats().stream()
                        .filter(seat -> seat.canceledAt() == null)
                        .map(PaymentReservationSeat::reservedSeatId)
                        .toList();
        if (!reservationActive.containsAll(itemIds)) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        return Math.multiplyExact(orderSnapshot.getSeatPrice(), itemIds.size());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeCancellation(PaymentOrder order, Long userId, List<Long> itemIds) {
        // 예매 좌석과 주문 좌석의 취소 시각이 같도록 한 번만 읽는다.
        Instant canceledAt = paymentClock.instant();
        var orderSnapshot = order.getReservationSnapshot();
        reservations.cancelPaidSeats(orderSnapshot.getReservationId(), userId, itemIds, canceledAt);
        orderSnapshot.cancelSeats(itemIds, canceledAt);
    }

    @Override
    public FinalizationFailure classifyFinalizationFailure(ErrorCode code) {
        return switch (code) {
            case SEAT_BUSY -> FinalizationFailure.RETRY;
            case SEAT_OWNER_MISMATCH, RESERVATION_NOT_FOUND, HOLD_NOT_ACTIVE, SCREENING_SEATS_NOT_READY ->
                    FinalizationFailure.COMPENSATE_APPROVAL;
            default -> FinalizationFailure.PENDING;
        };
    }

    @Override
    public String approvalCompensationReason() {
        return "예약 확정 실패 보상 취소";
    }
}
