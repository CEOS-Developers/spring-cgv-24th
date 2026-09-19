package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {
    private final MovieRepository movieRepository;

    @Transactional(readOnly = true)
    public List<Movie> findAll(){
        return movieRepository.findAllByVisibility(MovieVisibility.PUBLIC);
    }

    @Transactional(readOnly = true)
    public Movie findById(Long movieId){
        return movieRepository.findByIdAndVisibility(movieId, MovieVisibility.PUBLIC)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
    }

    public Movie create(MovieCreateRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .description(request.description())
                .runningTime(request.runningTime())
                .releaseDate(request.releaseDate())
                .ageRating(request.ageRating())
                .build();
        return movieRepository.save(movie);
    }

    @Transactional
    public void delete(Long movieId) {
        Movie movie = movieRepository.findByIdForUpdate(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        movie.hide();
    }
}
