package com.ceos.cgv.domain.reservation.repository;

import com.ceos.cgv.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @EntityGraph(attributePaths = "reservedSeats")
    Optional<Reservation> findWithSeatsById(Long reservationId);
}
