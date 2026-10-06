package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.CreateMovieRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieAdminService {

    private final MovieRepository movieRepository;

    @Transactional
    public void createMovie(CreateMovieRequest request) {
        movieRepository.save(new Movie(request.title()));
    }
}
