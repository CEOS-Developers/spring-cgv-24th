package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.MovieInfo;
import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieService {

    private final ScreeningRepository screeningRepository;

    @Transactional(readOnly = true)
    public GetMovieResponse getMovies(Long theaterId) {
        return new GetMovieResponse(
                screeningRepository.findDistinctMoviesByTheaterId(theaterId).stream()
                .map(MovieInfo::from).toList()
        );
    }
}
