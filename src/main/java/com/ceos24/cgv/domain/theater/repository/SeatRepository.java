package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Seat;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreeningId(Long screeningId);

    Optional<Seat> findByScreeningIdAndSeatNumber(Long screeningId, Long seatNumber);
}
