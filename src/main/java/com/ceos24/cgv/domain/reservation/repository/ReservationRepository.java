package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query(
            "SELECT r FROM Reservation r JOIN FETCH r.seat s WHERE r.member.id = :memberId AND s.screening.id = :screeningId AND s.seatNumber = :seatNumber")
    Optional<Reservation> findReservationToCancel(
            @Param("memberId") Long memberId,
            @Param("screeningId") Long screeningId,
            @Param("seatNumber") Long seatNumber);
}
