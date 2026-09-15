package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MovieImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieImageRepository extends JpaRepository<MovieImage, Long> {
    List<MovieImage> findByMovieId(Long movieId);
    List<MovieImage> findByMovieIdIn(List<Long> movieIds);
}
