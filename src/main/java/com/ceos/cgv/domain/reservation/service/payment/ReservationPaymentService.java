package com.ceos.cgv.domain.reservation.service.payment;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.service.seat.ReservationSeatLifecycle;
import com.ceos.cgv.domain.reservation.service.seat.ScreeningSeatLockService;
import com.ceos.cgv.domain.reservation.value.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
// 호출자는 READ_COMMITTED 트랜잭션으로 현재 잠금 값과 refresh 조회를 일치시킨다.
@Transactional(propagation = Propagation.MANDATORY)
public class ReservationPaymentService {
    private final ReservationRepository reservationRepository;
    private final MovieRepository movieRepository;
    private final ScreeningSeatLockService seatLockService;
    private final EntityManager entityManager;
    private final Clock seatHoldClock;

    public PaymentReservationSnapshot lockOwned(Long reservationId, Long userId) {
        Reservation reservation = owned(reservationId, userId);
        return new PaymentReservationSnapshot(reservation.getId(), reservation.getUser().getId(),
                reservation.getScreening().getId(), reservation.getScreening().getMovie().getTitle(),
                reservation.getStatus(), reservation.getExpiresAt(),
                reservation.getReservedSeats().stream().map(seat -> new PaymentReservationSeat(
                        seat.getId(), seat.getSeatRow(), seat.getSeatNumber(), seat.getCanceledAt())).toList());
    }

    public void startPayment(Long reservationId, Long userId) {
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() != ReservationStatus.HELD) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        Movie movie = movieRepository.findByIdForShare(reservation.getScreening().getMovie().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        // 예약 snapshot이 먼저 영화를 읽었어도 공유 잠금 후 최신 공개 상태로 확인한다.
        entityManager.refresh(movie);
        movie.ensurePublic();
        List<ScreeningSeat> seats = lockSeats(reservation, reservation.getReservedSeats());
        ReservationSeatLifecycle.verifyOccupancy(seats, reservation);
        reservation.startPayment(seatHoldClock.instant());
    }

    public void confirmPayment(Long reservationId, Long userId) {
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.RESERVED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        List<ScreeningSeat> seats = lockSeats(reservation, reservation.getReservedSeats());
        ReservationSeatLifecycle.verifyOccupancy(seats, reservation);
        reservation.confirmPayment();
    }

    public void releaseFailedPayment(Long reservationId, Long userId) {
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        List<ScreeningSeat> seats = lockSeats(reservation, reservation.getReservedSeats());
        ReservationSeatLifecycle.verifyOccupancy(seats, reservation);
        reservation.releaseFailedPayment();
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
    }

    public void cancelPaidSeats(Long reservationId, Long userId, List<Long> reservedSeatIds) {
        if (reservedSeatIds == null || reservedSeatIds.isEmpty()
                || reservedSeatIds.stream().anyMatch(id -> id == null)
                || new HashSet<>(reservedSeatIds).size() != reservedSeatIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
        }
        Set<Long> requested = Set.copyOf(reservedSeatIds);
        List<ReservedSeat> selected = reservation.getReservedSeats().stream()
                .filter(seat -> requested.contains(seat.getId())).toList();
        if (selected.size() != requested.size()) {
            throw new BusinessException(ErrorCode.INVALID_SEAT);
        }
        if (selected.stream().anyMatch(ReservedSeat::isCanceled)) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        List<ScreeningSeat> seats = lockSeats(reservation, selected);
        ReservationSeatLifecycle.verifyOccupancy(seats, reservation);
        Instant now = seatHoldClock.instant();
        selected.forEach(seat -> seat.cancel(now));
        seats.forEach(seat -> seat.releaseIfOwnedBy(reservation));
        reservation.completeSeatCancellation();
    }

    private Reservation owned(Long reservationId, Long userId) {
        // 같은 트랜잭션의 승인/부분 취소 직후 다시 조회해도 refresh가 변경을 잃지 않게 한다.
        entityManager.flush();
        Reservation reservation;
        try {
            reservation = reservationRepository.lockByIdNowait(reservationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        } catch (PessimisticLockingFailureException | LockTimeoutException | PessimisticLockException exception) {
            throw new BusinessException(ErrorCode.SEAT_BUSY);
        } catch (DataAccessException | PersistenceException exception) {
            if (isMysqlNowait(exception)) {
                throw new BusinessException(ErrorCode.SEAT_BUSY);
            }
            throw exception;
        }
        // 잠금 쿼리도 1차 캐시의 객체를 반환하므로 잠금 획득 뒤 DB 상태를 다시 읽는다.
        entityManager.refresh(reservation);
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        reservation.getReservedSeats().forEach(entityManager::refresh);
        return reservation;
    }

    private List<ScreeningSeat> lockSeats(Reservation reservation, List<ReservedSeat> histories) {
        if (histories.isEmpty() || histories.stream().anyMatch(seat -> seat.getScreeningSeat() == null)) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        List<SeatCoordinate> coordinates = histories.stream()
                .map(seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber())).toList();
        List<ScreeningSeat> seats = seatLockService.lockSeats(reservation.getScreening().getId(), coordinates);
        seats.forEach(entityManager::refresh);
        return seats;
    }

    private static boolean isMysqlNowait(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && sql.getErrorCode() == 3572 && "HY000".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
