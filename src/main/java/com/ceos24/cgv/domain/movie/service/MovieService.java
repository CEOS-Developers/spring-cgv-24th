package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    // 영화 생성
    @Transactional
    public Long createMovie(MovieCreateRequest request) {
        Movie movie = Movie.create(request.name(), request.releaseDate());
        movieRepository.save(movie);
        return movie.getId();
    }

    // 영화 목록 조회
    public List<MovieResponse> getMovies() {
        List<Movie> movies = movieRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        List<MovieResponse> responses = new ArrayList<>();
        for (Movie movie : movies) {
            MovieResponse response = MovieResponse.from(movie);
            responses.add(response);
        }

        return responses;
    }

    // 영화 조회
    public MovieResponse getMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));

        return MovieResponse.from(movie);
    }
}
