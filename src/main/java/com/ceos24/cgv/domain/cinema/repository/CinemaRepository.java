package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CinemaRepository extends JpaRepository<Cinema, Long> {

    Optional<Cinema> findByIdAndActiveTrue(Long cinemaId);

    List<Cinema> findAllByActiveTrueOrderByIdAsc();

    boolean existsByIdAndActiveTrue(Long cinemaId);
}
