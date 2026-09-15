package com.ceos24.spring_cgv.domain.movie.repository;

import com.ceos24.spring_cgv.domain.movie.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {
}
