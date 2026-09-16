package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {}
