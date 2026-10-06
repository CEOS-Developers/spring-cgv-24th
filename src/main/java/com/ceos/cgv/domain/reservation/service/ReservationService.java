package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservationResponse;
import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos.cgv.domain.reservation.service.exception.ExpiredHoldEncountered;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldExpiryService;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationCreationService creationService;
    private final ReservationRepository reservationRepository;
    private final ScreeningSeatLockService screeningSeatLockService;
    private final SeatHoldExpiryService seatHoldExpiryService;
    private final Clock seatHoldClock;

    public ReservationResponse create(ReservationCreateRequest request) {
        int maxCleanups = request.seats() == null ? 0 : request.seats().size();
        for (int attempt = 0; attempt <= maxCleanups; attempt++) {
            try {
                return creationService.create(request);
            } catch (ExpiredHoldEncountered expired) {
                seatHoldExpiryService.expireIfElapsed(expired.reservationId());
            }
        }
        throw new BusinessException(ErrorCode.SEAT_BUSY);
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long reservationId, Long userId) {
        return ReservationResponse.from(owned(reservationId, userId), seatHoldClock.instant());
    }

    private Reservation owned(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findWithSeatsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return reservation;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long reservationId, Long userId) {
        Reservation reservation = owned(reservationId, userId);
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
        }
        List<ReservedSeat> histories = reservation.getReservedSeats();
        long linkedCount = histories.stream().filter(seat -> seat.getScreeningSeat() != null).count();
        if (linkedCount > 0 && linkedCount != histories.size()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        if (linkedCount > 0) {
            List<SeatCoordinate> coordinates = histories.stream()
                    .map(seat -> new SeatCoordinate(seat.getSeatRow(), seat.getSeatNumber()))
                    .toList();
            List<ScreeningSeat> locked = screeningSeatLockService.lockSeats(
                    reservation.getScreening().getId(), coordinates);
            ReservationSnapshot current = reservationRepository.findSnapshotById(reservationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
            if (current.status() == ReservationStatus.CANCELED) {
                throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELED);
            }
            if (current.status() != ReservationStatus.RESERVED) {
                throw new BusinessException(ErrorCode.HOLD_NOT_ACTIVE);
            }
            ReservationSeatLifecycle.cancel(reservation, locked);
            return;
        }
        reservation.cancel();
    }

}
