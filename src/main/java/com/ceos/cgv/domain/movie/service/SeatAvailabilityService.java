package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.dto.ScreeningSeatLayout;
import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse;
import com.ceos.cgv.domain.movie.dto.SeatOccupancy;
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

/**
 * 좌석 목록은 엔티티를 만들지 않고 필요한 컬럼만 읽는 두 번의 조회로 구성한다.
 * 상영 정보(영화 공개 여부, 상영관 크기) 1회, 좌석과 점유 예약 상태 1회다.
 */
@Service
@RequiredArgsConstructor
public class SeatAvailabilityService {
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;
    private final Clock seatHoldClock;

    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> findByScreeningId(Long screeningId) {
        ScreeningSeatLayout layout = screeningRepository.findSeatLayoutById(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));
        if (layout.movieVisibility() != MovieVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.MOVIE_NOT_FOUND);
        }
        List<SeatOccupancy> seats = screeningSeatRepository.findSeatOccupanciesByScreeningId(screeningId);
        if (seats.size() != layout.expectedSeatCount()) {
            throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
        }
        Instant now = seatHoldClock.instant();
        return seats.stream().map(seat -> new SeatAvailabilityResponse(
                seat.seatRow(), seat.seatNumber(), statusOf(seat, now))).toList();
    }

    private static SeatAvailabilityResponse.Status statusOf(SeatOccupancy seat, Instant now) {
        ReservationStatus occupantStatus = seat.reservationStatus();
        if (occupantStatus == null) {
            return SeatAvailabilityResponse.Status.AVAILABLE;
        }
        if (occupantStatus == ReservationStatus.HELD || occupantStatus == ReservationStatus.PAYMENT_PENDING) {
            return Reservation.isExpiredAt(occupantStatus, seat.reservationExpiresAt(), now)
                    ? SeatAvailabilityResponse.Status.AVAILABLE
                    : SeatAvailabilityResponse.Status.HELD;
        }
        if (occupantStatus == ReservationStatus.RESERVED) {
            return SeatAvailabilityResponse.Status.RESERVED;
        }
        throw new BusinessException(ErrorCode.SCREENING_SEATS_NOT_READY);
    }
}
