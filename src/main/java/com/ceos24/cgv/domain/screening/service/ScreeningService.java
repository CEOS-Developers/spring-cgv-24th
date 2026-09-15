package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.cinema.entity.Auditorium;
import com.ceos24.cgv.domain.cinema.entity.Seat;
import com.ceos24.cgv.domain.cinema.repository.AuditoriumRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.cinema.repository.SeatRepository;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.domain.screening.dto.request.ScreeningCreateRequest;
import com.ceos24.cgv.domain.screening.dto.response.ScreeningResponse;
import com.ceos24.cgv.domain.screening.dto.response.SeatResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScreeningService {

    private final ScreeningRepository screeningRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final MovieRepository movieRepository;
    private final CinemaRepository cinemaRepository;
    private final SeatRepository seatRepository;
    private final ReservationSeatRepository reservationSeatRepository;

    // 상영 정보 등록
    @Transactional
    public Long createScreening(
            ScreeningCreateRequest request
    ) {
        // 영화 조회
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));

        // 상영관 조회
        Auditorium auditorium = auditoriumRepository.findById(request.auditoriumId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUDITORIUM_NOT_FOUND));

        // 종료 시간이 시작 시간보다 뒤인지 검증
        if (!request.startsAt().isBefore(request.endsAt())) {
            throw new BusinessException(ErrorCode.INVALID_SCREENING_TIME);
        }

        // 해당 상영관의 기존 상영 시간과 겹치는지 검증
        validateScreeningTime(
                auditorium.getId(),
                request.startsAt(),
                request.endsAt()
        );

        Screening screening = Screening.create(
                auditorium,
                movie,
                request.startsAt(),
                request.endsAt(),
                request.price()
        );

        return screeningRepository.save(screening).getId();
    }

    // 상영정보 조회
    public List<ScreeningResponse> getScreeningsByCinema(Long cinemaId) {
        if (!cinemaRepository.existsById(cinemaId)) {
            throw new BusinessException(
                    ErrorCode.CINEMA_NOT_FOUND
            );
        }

        return screeningRepository
                .findAllByAuditorium_Cinema_IdOrderByStartsAtAsc(
                        cinemaId
                )
                .stream()
                .map(ScreeningResponse::from)
                .toList();
    }

    // 해당 상영관의 기존 상영 시간과 겹치는지 검증
    private void validateScreeningTime(
            Long auditoriumId,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorCode.INVALID_SCREENING_TIME);
        }

        boolean overlapping = screeningRepository
                        .existsByAuditorium_IdAndStartsAtLessThanAndEndsAtGreaterThan(
                                auditoriumId,
                                endsAt,
                                startsAt
                        );

        if (overlapping) {
            throw new BusinessException(ErrorCode.SCREENING_TIME_CONFLICT);
        }
    }

    // 상영회차의 상영관 좌석 상태 조회
    public List<SeatResponse> getSeatsByScreening(Long screeningId) {

        // 상영 정보 조회
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREENING_NOT_FOUND));

        // 상영관의 전체 좌석 조회
        Long auditoriumId = screening.getAuditorium().getId();
        List<Seat> seats = seatRepository.
                findAllByAuditoriumIdOrderByRowNumberAscColumnNumberAsc(auditoriumId);

        // 해당 상영 회차에서 CONFIRMED 상태인 좌석 ID 조회
        List<Long> reservedSeatIdList =
                reservationSeatRepository.
                        findSeatIdsByScreeningIdAndStatus(screeningId, ReservationStatus.CONFIRMED);

        // 좌석마다 reserved 여부 계산
        Set<Long> reservedSeatIds = new HashSet<>(reservedSeatIdList);

        // SeatResponse 목록 반환
        return seats.stream()
                .map(seat ->
                        SeatResponse.from(
                                seat,
                                reservedSeatIds.contains(seat.getId())
                        )
                )
                .toList();
    }
}
