package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.screening.dto.request.CreateScreeningRequest;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.service.SeatService;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ScreeningAdminService {

    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final ScreeningRepository screeningRepository;
    private final SeatService seatService;

    @Transactional
    public void createScreening(CreateScreeningRequest request) {
        Movie movie = movieRepository
                .findById(request.movieId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        Screen screen = screenRepository
                .findById(request.screenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SCREEN_NOT_FOUND));
        Screening screening = new Screening(movie, screen, request.startTime(), request.endTime());
        screeningRepository.save(screening);
        seatService.createSeats(screening);
    }
}
