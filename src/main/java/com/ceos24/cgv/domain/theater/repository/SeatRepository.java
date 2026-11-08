package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Seat;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreeningId(Long screeningId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Seat> findByScreeningIdAndSeatNumber(Long screeningId, Long seatNumber);
}
