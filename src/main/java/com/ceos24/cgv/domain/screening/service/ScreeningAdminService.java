package com.ceos24.cgv.domain.screening.service;

import com.ceos24.cgv.domain.movie.domain.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.screening.domain.Screening;
import com.ceos24.cgv.domain.screening.dto.request.CreateScreeningRequest;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.domain.Screen;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.service.SeatService;
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
        Movie movie = movieRepository.findById(request.movieId()).orElseThrow();
        Screen screen = screenRepository.findById(request.screenId()).orElseThrow();
        Screening screening = new Screening(movie, screen, request.startTime(), request.endTime());
        screeningRepository.save(screening);
        seatService.createSeats(screening);
    }
}
