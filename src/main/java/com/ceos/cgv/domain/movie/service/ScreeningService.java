package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.cinema.entity.Screen;
import com.ceos.cgv.domain.cinema.repository.ScreenRepository;
import com.ceos.cgv.domain.movie.dto.ScreeningCreateRequest;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.movie.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScreeningService {
    private final MovieRepository movieRepository;
    private final ScreenRepository screenRepository;
    private final ScreeningRepository screeningRepository;

    public Screening create(ScreeningCreateRequest request) {
        Movie movie = movieRepository.findById(request.movieId())
                .orElseThrow(() -> new IllegalArgumentException("영화를 찾을 수 없습니다."));
        Screen screen = screenRepository.findById(request.screenId())
                .orElseThrow(() -> new IllegalArgumentException("상영관을 찾을 수 없습니다."));
        return screeningRepository.save(new Screening(movie, screen, request.startAt()));
    }

    public List<Screening> findAllByMovieId(Long movieId) {
        if (!movieRepository.existsById(movieId)) {
            throw new IllegalArgumentException("영화를 찾을 수 없습니다.");
        }
        return screeningRepository.findAllByMovie_IdOrderByStartAt(movieId);
    }
}
