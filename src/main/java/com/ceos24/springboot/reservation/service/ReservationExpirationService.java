package com.ceos24.springboot.reservation.service;

import com.ceos24.springboot.reservation.domain.ReservationStatus;
import com.ceos24.springboot.reservation.repository.ReservationRepository;
import com.ceos24.springboot.reservation.repository.ReservationSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class ReservationExpirationService {
    private final Clock clock;
    private final ReservationRepository reservationRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    // 만료 해제는 새 예매가 실패하더라도 유지되도록 별도 트랜잭션에서 처리한다.
    public void releaseExpiredSeats(Long screeningId, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            return;
        }
        reservationSeatRepository.findExpiredReservationIdsBySeats(
                screeningId, seatIds, ReservationStatus.Pending, LocalDateTime.now(clock)
        ).forEach(this::expireLockedReservation);
    }

    public void releaseExpiredReservationsForUser(Long userId) {
        reservationRepository.findExpiredReservationIdsByUser(
                userId, ReservationStatus.Pending, LocalDateTime.now(clock)
        ).forEach(this::expireLockedReservation);
    }

    public void expireReservation(Long reservationId) {
        expireLockedReservation(reservationId);
    }

    private void expireLockedReservation(Long reservationId) {
        reservationRepository.findByIdWithLock(reservationId).ifPresent(reservation -> {
            // 락을 기다리는 동안 확정되었을 수 있으므로 상태와 시간을 다시 확인한다.
            if (reservation.expireIfPending(LocalDateTime.now(clock))) {
                reservationSeatRepository.deleteAllByReservation_ReservationId(reservationId);
            }
        });
    }
}
