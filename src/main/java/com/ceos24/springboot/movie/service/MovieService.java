package com.ceos24.springboot.movie.service;

import com.ceos24.springboot.global.exception.ErrorCode;
import com.ceos24.springboot.global.exception.GlobalException;
import com.ceos24.springboot.movie.domain.Movie;
import com.ceos24.springboot.movie.dto.MovieCreateRequest;
import com.ceos24.springboot.movie.dto.MovieResponse;
import com.ceos24.springboot.movie.repository.MovieRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor //final 필드를 생성자로 주입해줌
@Transactional(readOnly = true) //기본적으로 조회용 트랜잭션으로 동작
public class MovieService {

//    DB 접근에 사용
    private final MovieRepository movieRepository;

    // 영화 등록
    @Transactional
    public MovieResponse createMovie(MovieCreateRequest request) {
        Movie savedMovie = movieRepository.save(request.toEntity());
        return MovieResponse.from(savedMovie);
    }

    // 영화 전체 조회
    public List<MovieResponse> getMovies() {
        return movieRepository.findAll()
                .stream()
                .map(MovieResponse::from)
                .toList();
    }

    // 영화 하나 조회
    public MovieResponse getMovie(Long movieId) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() ->
                        new GlobalException(ErrorCode.MOVIE_NOT_FOUND)
                );

        return MovieResponse.from(movie);
    }
}