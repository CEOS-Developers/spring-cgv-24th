package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreeningId(Long screeningId);

    Seat findByScreeningIdAndSeatNumber(Long screeningId, Long seatNumber);
}
