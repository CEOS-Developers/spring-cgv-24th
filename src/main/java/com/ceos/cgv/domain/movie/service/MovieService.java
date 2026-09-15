package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
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
                .orElseThrow(()-> new IllegalArgumentException("영화를 찾을 수 없습니다."));
    }
}
