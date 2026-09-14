package com.ceos.cgv.domain.cinema.repository;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {
}
