package com.cgvclone.cgv.domain.movie;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.movie.dto.MovieDetailResponse;
import com.cgvclone.cgv.domain.movie.dto.MovieListResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieService {

    private final MovieRepository movieRepository;

    public Movie getMovieEntity(Long movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_NOT_FOUND));
    }

    public MovieListResponse getMovies() {
        List<Movie> movies = movieRepository.findAll();
        return MovieListResponse.from(movies);
    }

    public MovieDetailResponse getMovie(Long movieId) {
        Movie movie = getMovieEntity(movieId);
        return MovieDetailResponse.from(movie);
    }
}
