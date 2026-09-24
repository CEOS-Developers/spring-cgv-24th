package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatHoldExpiryService {
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ScreeningSeatLockService seatLockService;
    private final Clock seatHoldClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean expireIfElapsed(Long reservationId) {
        ReservationSnapshot snapshot = reservationRepository.findSnapshotById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        Instant now = seatHoldClock.instant();
        if (snapshot.status() != ReservationStatus.HELD
                || snapshot.expiresAt() == null || now.isBefore(snapshot.expiresAt())) {
            return false;
        }
        List<SeatCoordinate> coordinates = reservedSeatRepository.findCoordinatesByReservationId(reservationId);
        List<ScreeningSeat> seats = seatLockService.lockSeats(snapshot.screeningId(), coordinates);
        Reservation reservation = reservationRepository.findWithSeatsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        now = seatHoldClock.instant();
        if (!reservation.isExpiredAt(now)) {
            return false;
        }
        for (ScreeningSeat seat : seats) {
            if (seat.getCurrentReservation() == null
                    || !seat.getCurrentReservation().getId().equals(reservationId)) {
                throw new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH);
            }
        }
        reservation.expire(now);
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
        return true;
    }
}
