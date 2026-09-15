package com.ceos24.springboot.reservation.repository;

import com.ceos24.springboot.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {
}