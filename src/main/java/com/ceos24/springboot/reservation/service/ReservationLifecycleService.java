package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.repository.PaymentRepository;
import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationLifecycleService {
    private final ReservationSeatRepository seats;
    private final PaymentRepository payments;

    // 호출자는 예매 락을 보유해야 한다. 상태 변경과 좌석 해제는 함께 커밋한다.
    public void cancelLocked(Reservation reservation) {
        reservation.cancel();
        closePendingPayment(reservation, false);
        releaseSeats(reservation);
    }

    public void expireLocked(Reservation reservation, LocalDateTime now) {
        if (reservation.expireIfPending(now)) {
            closePendingPayment(reservation, true);
            releaseSeats(reservation);
        }
    }

    public void failPaymentLocked(Reservation reservation, LocalDateTime now) {
        reservation.failPayment(now);
        releaseSeats(reservation);
    }

    public void refundLocked(Reservation reservation) {
        reservation.cancelAfterRefund();
        releaseSeats(reservation);
    }

    private void closePendingPayment(Reservation reservation, boolean expired) {
        payments.findFirstByTargetTypeAndTargetIdOrderByIdDesc(
                        PaymentTargetType.RESERVATION, reservation.getReservationId())
                .ifPresent(existing -> {
                    var payment = payments.findMyPaymentWithLock(existing.getOrderId(), reservation.getUser().getId())
                            .orElseThrow(() -> new GlobalException(ErrorCode.PAYMENT_NOT_FOUND));
                    // PG가 실패를 확정한 결제는 그대로 보존하고 예매만 종료할 수 있다.
                    if (payment.getStatus() != PaymentStatus.FAILED) {
                        payment.closePending(expired);
                    }
                });
    }

    private void releaseSeats(Reservation reservation) {
        seats.deleteAllByReservation_ReservationId(reservation.getReservationId());
    }
}
