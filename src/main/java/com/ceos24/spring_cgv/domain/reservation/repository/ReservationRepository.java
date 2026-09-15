package com.ceos24.spring_cgv.domain.reservation.repository;

import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
}
