package com.ceos24.spring_cgv.domain.reservation.repository;

import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
}
