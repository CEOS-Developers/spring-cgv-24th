package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByAuditoriumIdOrderByRowNumberAscColumnNumberAsc(
            Long auditoriumId
    );
}
