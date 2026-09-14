package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    List<ReservationSeat> findByScreeningId(Long screeningId);
}
