package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
import com.ceos.cgv.domain.reservation.service.result.HoldTransitionResult;
import com.ceos.cgv.domain.reservation.service.seat.ReservationSeatLifecycle;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatHoldTransitionService {
    private final ReservationRepository reservationRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ScreeningSeatLockService seatLockService;
    private final Clock seatHoldClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldTransitionResult release(Long reservationId, Long userId) {
        ReservationSnapshot snapshot = snapshot(reservationId, userId);
        if (snapshot.status() != ReservationStatus.HELD) {
            return HoldTransitionResult.failure(ErrorCode.HOLD_NOT_ACTIVE);
        }
        List<ScreeningSeat> seats = lockSeats(snapshot.screeningId(), reservationId);
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return HoldTransitionResult.failure(ErrorCode.HOLD_NOT_ACTIVE);
        }
        Instant now = seatHoldClock.instant();
        if (reservation.isExpiredAt(now)) {
            ReservationSeatLifecycle.expire(reservation, seats, now);
            return HoldTransitionResult.failure(ErrorCode.HOLD_EXPIRED);
        }
        ReservationSeatLifecycle.release(reservation, seats);
        return HoldTransitionResult.success(SeatHoldResponse.from(reservation));
    }

    private Reservation owned(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findWithSeatsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return reservation;
    }

    private ReservationSnapshot snapshot(Long reservationId, Long userId) {
        ReservationSnapshot snapshot = reservationRepository.findSnapshotById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!snapshot.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return snapshot;
    }

    private List<ScreeningSeat> lockSeats(Long screeningId, Long reservationId) {
        List<SeatCoordinate> coordinates =
                reservedSeatRepository.findCoordinatesByReservationId(reservationId);
        return seatLockService.lockSeats(screeningId, coordinates);
    }

}
