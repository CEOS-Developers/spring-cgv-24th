package com.ceos24.cgv.domain.reservation.service;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentTargetType;
import com.ceos24.cgv.domain.payment.service.target.PaymentTargetHandler;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationPaymentHandler implements PaymentTargetHandler {
    private final ReservationRepository reservations;

    @Override
    public PaymentTargetType type() { return PaymentTargetType.RESERVATION; }

    @Override
    public void lockTarget(Long targetId) {
        reservations.findByIdForUpdate(targetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
    }

    @Override
    public boolean isPayable(Payment payment) {
        return payment.getReservation().getStatus() == ReservationStatus.PENDING_PAYMENT;
    }

    @Override
    public void validateCancellation(Payment payment, long amount) {
        // 좌석별 취소 항목이 없는 금액 취소로 일부 좌석을 임의 해제하지 않는다.
        if (amount != payment.getTotalAmount() || payment.getCanceledAmount() != 0) {
            throw new BusinessException(ErrorCode.RESERVATION_PARTIAL_CANCEL_NOT_SUPPORTED);
        }
    }

    @Override
    public void afterApproval(Payment payment) { payment.getReservation().confirm(); }

    @Override
    public void afterPartialCancellation(Payment payment) {
        throw new IllegalStateException("좌석별 부분 취소가 연결되지 않았습니다.");
    }

    @Override
    public void afterFullCancellation(Payment payment) { payment.getReservation().cancel(); }

    @Override
    public void afterConfirmationRejected(Payment payment) { payment.getReservation().cancel(); }

    @Override
    public void afterExpiration(Payment payment) { payment.getReservation().expire(); }
}
