package com.ceos24.cgv.domain.movie.repository;

import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MovieStatisticsRepository extends JpaRepository<MovieStatistics, Long> {
    Optional<MovieStatistics> findByMovieId(Long movieId);
}
