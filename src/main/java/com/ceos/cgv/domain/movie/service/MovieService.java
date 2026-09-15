package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {
    private final MovieRepository movieRepository;

    public List<Movie> findAll(){
        return movieRepository.findAll();
    }

    public Movie findById(Long movieId){
        return movieRepository.findById(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
    }

    public Movie create(MovieCreateRequest request) {
        Movie movie = new Movie(
                request.title(),
                request.description(),
                request.runningTime(),
                request.releaseDate(),
                request.ageRating()
        );
        return movieRepository.save(movie);
    }

    public void delete(Long movieId) {
        movieRepository.delete(findById(movieId));
    }
}
