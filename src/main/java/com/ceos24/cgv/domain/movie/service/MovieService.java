package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieStatistic;
import com.ceos24.cgv.domain.movie.exception.MovieErrorCode;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    //영화 생성
    @Transactional
    public MovieDetailResponse createMovie(MovieRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .runningTime(request.runningTime())
                .description(request.description())
                .openDate(request.openDate())
                .endDate(request.endDate())
                .status(request.status())
                .build();

        movie.assignMovieStatistic(MovieStatistic.createDefault());

        Movie savedMovie = movieRepository.save(movie);

        return MovieDetailResponse.from(savedMovie);

    }
    // 영화 목록 조회
    public List<MovieListResponse> getMovies() {
        return movieRepository.findAllByOrderByOpenDateDescIdDesc()
                .stream()
                .map(MovieListResponse::from)
                .toList();
    }

    // 영화 상세 조회
    public MovieDetailResponse getMovie(Long movieId) {
        Movie movie = findMovieWithStatistic(movieId);

        return MovieDetailResponse.from(movie);
    }

    //영화와 연결된 통계 삭제
    @Transactional
    public void deleteMovie(Long movieId) {
        Movie movie = findMovieWithStatistic(movieId);

        movieRepository.delete(movie);
    }

    private Movie findMovieWithStatistic(Long movieId) {
        return movieRepository.findWithMovieStatisticById(movieId)
                .orElseThrow(() ->
                        new GeneralException(MovieErrorCode.MOVIE_NOT_FOUND)
                );
    }


}
