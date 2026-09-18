package com.spring_cgv_24th.domain.screening.service;

import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.domain.screening.dto.request.ScreeningReqDTO;
import com.spring_cgv_24th.domain.screening.dto.response.ScreeningResDTO;
import com.spring_cgv_24th.domain.screening.dto.response.ScreeningSeatResDTO;
import com.spring_cgv_24th.domain.screening.entity.Screening;
import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import com.spring_cgv_24th.domain.screening.repository.ScreeningRepository;
import com.spring_cgv_24th.domain.screening.repository.ScreeningSeatRepository;
import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import com.spring_cgv_24th.domain.auditorium.entity.AuditoriumType;
import com.spring_cgv_24th.domain.auditorium.repository.AuditoriumRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreeningService {

    private final MovieRepository movieRepository;
    private final AuditoriumRepository auditoriumRepository;
    private final ScreeningRepository screeningRepository;
    private final ScreeningSeatRepository screeningSeatRepository;

    // 상영관의 회차 시간 중복을 확인하고 회차와 모든 좌석을 함께 생성한다.
    @Transactional
    public ScreeningResDTO createScreening(ScreeningReqDTO.CreateScreeningDTO request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new CustomException(ErrorCode.MOVIE_NOT_FOUND));

        Auditorium auditorium = auditoriumRepository.findById(request.auditoriumId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUDITORIUM_NOT_FOUND));
        AuditoriumType type = auditorium.getType();

        if (movie.getDurationMinutes() <= 0 || type.getRowCount() <= 0 || type.getColumnCount() <= 0) {
            throw new IllegalStateException("저장된 영화 상영 시간 또는 상영관 좌석 규격이 올바르지 않습니다.");
        }

        LocalDateTime startsAt = request.startsAt();
        LocalDateTime endsAt = startsAt.plusMinutes(movie.getDurationMinutes());
        if (screeningRepository.existsOverlapping(auditorium.getId(), startsAt, endsAt)) {
            throw new CustomException(ErrorCode.SCREENING_OVERLAP);
        }

        int price = type.getBasePrice();
        if (price <= 0) {
            throw new CustomException(ErrorCode.TICKET_PRICE_CONFIG_INVALID);
        }

        Screening screening = screeningRepository.save(Screening.builder()
                .movie(movie)
                .auditorium(auditorium)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());

        // 상영관의 행·열 전체 좌표를 순회하며 회차별 좌석을 하나씩 만든다.
        List<ScreeningSeat> seats = new ArrayList<>();
        for (int row = 1; row <= type.getRowCount(); row++) {
            for (int column = 1; column <= type.getColumnCount(); column++) {
                seats.add(ScreeningSeat.builder()
                        .screening(screening)
                        .rowNo((short) row)
                        .columnNo((short) column)
                        .price(price)
                        .build());
            }
        }
        screeningSeatRepository.saveAll(seats);

        return ScreeningResDTO.from(screening);
    }

    // 회차 ID로 상영 상세 정보를 조회한다.
    public ScreeningResDTO getScreening(Long screeningId) {
        Screening screening = screeningRepository.findDetailsById(screeningId)
                .orElseThrow(() -> new CustomException(ErrorCode.SCREENING_NOT_FOUND));
        return ScreeningResDTO.from(screening);
    }

    // 전체 상영 회차를 시작 시각 순으로 조회한다.
    public List<ScreeningResDTO> getScreenings() {
        return screeningRepository.findAllByOrderByStartsAtAscIdAsc().stream()
                .map(ScreeningResDTO::from)
                .toList();
    }

    // 회차의 좌석별 가격과 예매 가능 여부를 조회한다.
    public List<ScreeningSeatResDTO> getSeats(Long screeningId) {
        if (!screeningRepository.existsById(screeningId)) {
            throw new CustomException(ErrorCode.SCREENING_NOT_FOUND);
        }
        return screeningSeatRepository.findAllByScreeningIdOrderByRowNoAscColumnNoAsc(screeningId).stream()
                .map(ScreeningSeatResDTO::from)
                .toList();
    }
}
