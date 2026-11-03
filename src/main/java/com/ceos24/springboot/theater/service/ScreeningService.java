package com.ceos24.springboot.theater.service;

import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.movie.repository.MovieRepository;
import com.ceos24.springboot.theater.domain.Screen;
import com.ceos24.springboot.theater.domain.Screening;
import com.ceos24.springboot.theater.domain.ScreeningSeat;
import com.ceos24.springboot.theater.dto.ScreeningCreateRequest;
import com.ceos24.springboot.theater.dto.ScreeningResponse;
import com.ceos24.springboot.theater.repository.ScreenRepository;
import com.ceos24.springboot.theater.repository.ScreeningRepository;
import com.ceos24.springboot.theater.repository.TheaterRepository;
import com.ceos24.springboot.theater.repository.SeatRepository;
import com.ceos24.springboot.theater.repository.ScreeningSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScreeningService {

    private final ScreeningRepository screeningRepository;
    private final ScreenRepository screenRepository;
    private final MovieRepository movieRepository;

    // 추가
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;
    private final ScreeningSeatRepository screeningSeatRepository;




    // 상영회차 등록
    @Transactional
    public ScreeningResponse createScreening(
            ScreeningCreateRequest request
    ) {

        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화를 찾을 수 없습니다."
                        )
                );

        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 상영관을 찾을 수 없습니다."
                        )
                );

        Screening screening = Screening.builder()
                .movie(movie)
                .screen(screen)
                .screeningDate(request.screeningDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .screeningType(request.screeningType())
                .build();

        Screening savedScreening =
                screeningRepository.save(screening);

        // 빈좌석도 사용자가 점유 시 비관적에 걸릴 수 있도록
        screeningSeatRepository.saveAll(seatRepository
                .findAllByScreenId_ScreenIdOrderBySeatIdAsc(screen.getScreenId()).stream()
                .map(seat -> ScreeningSeat.create(savedScreening, seat)).toList());

        return ScreeningResponse.from(savedScreening);
    }


    // 특정 영화의 상영회차 조회
    public List<ScreeningResponse> getScreeningsByMovie(
            Long movieId
    ) {

        movieRepository.findById(movieId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화를 찾을 수 없습니다."
                        )
                );

        return screeningRepository
                .findByMovie_MovieId(movieId)
                .stream()
                .map(ScreeningResponse::from)
                .toList();
    }


    // 추가: 특정 영화관의 상영회차 조회
    public List<ScreeningResponse> getScreeningsByTheater(
            Long theaterId
    ) {

        theaterRepository.findById(theaterId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "해당 영화관을 찾을 수 없습니다."
                        )
                );

        return screeningRepository
                .findByScreen_Theater_TheaterId(theaterId)
                .stream()
                .map(ScreeningResponse::from)
                .toList();
    }


    // 특정 상영관의 상영회차
    public List<ScreeningResponse> getScreeningsByScreen(
            Long screenId
    ) {
        return screeningRepository.findByScreen_ScreenId(screenId)
                .stream()
                .map(ScreeningResponse::from)
                .toList();
    }


    // 특정 영화 + 특정 상영관의 상영회차
    public List<ScreeningResponse> getScreenings(
            Long movieId,
            Long screenId
    ) {
        return screeningRepository
                .findByMovie_MovieIdAndScreen_ScreenId(
                        movieId,
                        screenId
                )
                .stream()
                .map(ScreeningResponse::from)
                .toList();
    }


}