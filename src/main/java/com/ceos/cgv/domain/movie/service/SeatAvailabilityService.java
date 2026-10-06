package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningSeatRepository;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatAvailabilityService {
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final Clock seatHoldClock;

    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> findByScreeningId(Long screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        if (screening.getMovie().getVisibility() != MovieVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
        }
        List<ScreeningSeat> seats = screeningSeatRepository
                .findAllByScreening_IdOrderBySeatRowAscSeatNumberAsc(screeningId);
        if (seats.size() != (long) screening.getScreen().getRowCount()
                * screening.getScreen().getSeatsPerRow()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        Instant now = seatHoldClock.instant();
        return seats.stream().map(seat -> new SeatAvailabilityResponse(
                seat.getSeatRow(), seat.getSeatNumber(), statusOf(seat, now))).toList();
    }

    private static SeatAvailabilityResponse.Status statusOf(ScreeningSeat seat, Instant now) {
        Reservation occupant = seat.getCurrentReservation();
        if (occupant == null) {
            return SeatAvailabilityResponse.Status.AVAILABLE;
        }
        if (occupant.getStatus() == ReservationStatus.HELD) {
            return occupant.isExpiredAt(now)
                    ? SeatAvailabilityResponse.Status.AVAILABLE
                    : SeatAvailabilityResponse.Status.HELD;
        }
        if (occupant.getStatus() == ReservationStatus.RESERVED) {
            return SeatAvailabilityResponse.Status.RESERVED;
        }
        throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
    }
}
