package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
