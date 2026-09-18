package com.spring_cgv_24th.domain.reservation.repository;

import com.spring_cgv_24th.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
}
