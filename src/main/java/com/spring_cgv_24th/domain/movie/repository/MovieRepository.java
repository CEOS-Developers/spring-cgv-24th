package com.spring_cgv_24th.domain.movie.repository;

import com.spring_cgv_24th.domain.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
