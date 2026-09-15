package com.ceos.cgv.domain.reservation.repository;

import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {
    boolean existsByScreening_IdAndSeatRowAndSeatNumberAndReservation_Status(
            Long screeningId,
            String seatRow,
            Integer seatNumber,
            ReservationStatus status
    );
}
