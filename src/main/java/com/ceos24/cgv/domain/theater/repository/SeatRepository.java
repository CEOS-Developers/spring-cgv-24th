package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.domain.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreeningId(Long screeningId);

    Seat findByScreeningIdAndSeatNumber(Long screeningId, Long seatNumber);
}
