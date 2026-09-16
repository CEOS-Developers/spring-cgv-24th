package com.ceos.cgv.domain.reservation.repository;

import com.ceos.cgv.domain.reservation.entity.ReservedSeat;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservedSeatRepository extends JpaRepository<ReservedSeat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservedSeat.id
            from ReservedSeat reservedSeat
            where reservedSeat.reservation.screening.id = :screeningId
              and reservedSeat.seatRow = :seatRow
              and reservedSeat.seatNumber = :seatNumber
              and reservedSeat.reservation.status = :status
            """)
    Optional<Long> findIdByReservationScreeningIdAndSeatRowAndSeatNumberAndReservationStatus(
            @Param("screeningId") Long screeningId,
            @Param("seatRow") String seatRow,
            @Param("seatNumber") Integer seatNumber,
            @Param("status") ReservationStatus status
    );
}
