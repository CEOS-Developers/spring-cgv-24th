package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {}
