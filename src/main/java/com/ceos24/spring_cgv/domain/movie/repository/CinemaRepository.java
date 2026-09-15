package com.ceos24.spring_cgv.domain.movie.repository;

import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {
}
