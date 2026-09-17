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
@Transactional
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public Movie getMovieEntity(Long movieId) {
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new GlobalException(ErrorCode.MOVIE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public MovieListResponse getMovies() {
        List<Movie> movies = movieRepository.findAll();
        return MovieListResponse.from(movies);
    }

    @Transactional(readOnly = true)
    public MovieDetailResponse getMovie(Long movieId) {
        Movie movie = getMovieEntity(movieId);
        return MovieDetailResponse.from(movie);
    }
}
