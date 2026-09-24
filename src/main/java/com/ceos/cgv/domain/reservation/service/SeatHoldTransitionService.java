package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.domain.reservation.dto.SeatHoldResponse;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.repository.ReservedSeatRepository;
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
    private final MovieRepository movieRepository;
    private final ReservedSeatRepository reservedSeatRepository;
    private final ScreeningSeatLockService seatLockService;
    private final Clock seatHoldClock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransitionResult confirm(Long reservationId, Long userId) {
        ReservationSnapshot snapshot = snapshot(reservationId, userId);
        if (snapshot.status() == ReservationStatus.RESERVED) {
            Reservation current = owned(reservationId, userId);
            return current.getStatus() == ReservationStatus.RESERVED
                    ? TransitionResult.success(SeatHoldResponse.from(current))
                    : TransitionResult.failure(ErrorCode.HOLD_NOT_ACTIVE);
        }
        if (snapshot.status() != ReservationStatus.HELD) {
            return TransitionResult.failure(ErrorCode.HOLD_NOT_ACTIVE);
        }
        Movie movie = movieRepository.findByIdForShare(snapshot.movieId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        List<ScreeningSeat> seats = lockSeats(snapshot.screeningId(), reservationId);
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            return TransitionResult.success(SeatHoldResponse.from(reservation));
        }
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return TransitionResult.failure(ErrorCode.HOLD_NOT_ACTIVE);
        }
        verifyOccupancy(seats, reservation);
        Instant now = seatHoldClock.instant();
        if (reservation.isExpiredAt(now)) {
            reservation.expire(now);
            seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
            return TransitionResult.failure(ErrorCode.HOLD_EXPIRED);
        }
        if (movie.getVisibility() != MovieVisibility.PUBLIC) {
            reservation.release();
            seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
            return TransitionResult.failure(ErrorCode.MOVIE_NOT_AVAILABLE);
        }
        reservation.confirm(now);
        return TransitionResult.success(SeatHoldResponse.from(reservation));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ErrorCode release(Long reservationId, Long userId) {
        ReservationSnapshot snapshot = snapshot(reservationId, userId);
        if (snapshot.status() != ReservationStatus.HELD) {
            return ErrorCode.HOLD_NOT_ACTIVE;
        }
        List<ScreeningSeat> seats = lockSeats(snapshot.screeningId(), reservationId);
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return ErrorCode.HOLD_NOT_ACTIVE;
        }
        verifyOccupancy(seats, reservation);
        Instant now = seatHoldClock.instant();
        if (reservation.isExpiredAt(now)) {
            reservation.expire(now);
            seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
            return ErrorCode.HOLD_EXPIRED;
        }
        reservation.release();
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
        return null;
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

    private static void verifyOccupancy(List<ScreeningSeat> seats, Reservation reservation) {
        for (ScreeningSeat seat : seats) {
            if (seat.getCurrentReservation() == null
                    || !seat.getCurrentReservation().getId().equals(reservation.getId())) {
                throw new BusinessException(ErrorCode.SEAT_OWNER_MISMATCH);
            }
        }
    }

    public record TransitionResult(SeatHoldResponse response, ErrorCode error) {
        static TransitionResult success(SeatHoldResponse response) {
            return new TransitionResult(response, null);
        }

        static TransitionResult failure(ErrorCode error) {
            return new TransitionResult(null, error);
        }
    }
}
