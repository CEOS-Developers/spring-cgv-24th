package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository
        extends JpaRepository<ReservationSeat, Long> {

    @Query("""
            SELECT reservationSeat.seat.id
            FROM ReservationSeat reservationSeat
            WHERE reservationSeat.reservation.screening.id = :screeningId
            AND reservationSeat.reservation.status = :status
            AND reservationSeat.seat.id IN :seatIds
            """)
    List<Long> findReservedSeatIds(
            @Param("screeningId")
            Long screeningId,

            @Param("seatIds")
            List<Long> seatIds,

            @Param("status")
            ReservationStatus status
    );
}