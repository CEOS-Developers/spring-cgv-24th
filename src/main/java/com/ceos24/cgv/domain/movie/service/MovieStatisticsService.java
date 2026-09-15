package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieStatisticsRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieStatisticsRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovieStatisticsService {

    private final MovieStatisticsRepository movieStatisticsRepository;
    private final MovieRepository movieRepository;

    @Transactional
    public Long createOrUpdate(Long movieId, MovieStatisticsRequest request) {
        return movieStatisticsRepository.findByMovieId(movieId)
                .map(existing -> {
                    existing.update(
                            request.audienceCount(), request.reservationRate(),
                            request.eggScore(), request.reviewCount()
                    );
                    return existing.getId();
                })
                .orElseGet(() -> {
                    Movie movie = movieRepository.findById(movieId)
                            .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_NOT_FOUND));

                    MovieStatistics statistics = MovieStatistics.create(
                            movie, request.audienceCount(), request.reservationRate(),
                            request.eggScore(), request.reviewCount()
                    );
                    return movieStatisticsRepository.save(statistics).getId();
                });
    }
}
