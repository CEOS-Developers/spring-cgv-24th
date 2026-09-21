package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieImageCreateRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieImage;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovieImageService {

    private final MovieImageRepository movieImageRepository;
    private final MovieRepository movieRepository;

    @Transactional
    public Long create(Long movieId, MovieImageCreateRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new GeneralException(MovieErrorStatus.MOVIE_NOT_FOUND));

        MovieImage movieImage = MovieImage.create(movie, request.movieImageUrl(), request.type());
        return movieImageRepository.save(movieImage).getId();
    }
}
