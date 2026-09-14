package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {}
