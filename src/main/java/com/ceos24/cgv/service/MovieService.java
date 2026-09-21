package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.Movie;
import com.ceos24.cgv.dto.response.MovieResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    public List<MovieResponse> findAll() {
        return movieRepository.findAll().stream()
                .map(MovieResponse::from)
                .toList();
    }

    public MovieResponse findById(Long id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.MOVIE_NOT_FOUND));
        return MovieResponse.from(movie);
    }
}
