package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.SeatReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatReservationRepository extends JpaRepository<SeatReservation, Long> {

    @Query(
            """
        SELECT sr
        FROM SeatReservation sr
        JOIN FETCH sr.reservation r
        WHERE sr.screening.id = :screeningId
    """)
    List<SeatReservation> findAllWithReservationByScreeningId(@Param("screeningId") Long screeningId);

    @Query(
            """
        SELECT sr
        FROM SeatReservation sr
        JOIN FETCH sr.reservation r
        WHERE sr.screening.id = :screeningId AND sr.seat.id IN :seatIds
    """)
    List<SeatReservation> findAllWithReservationByScreeningIdAndSeatIdIn(
            @Param("screeningId") Long screeningId, @Param("seatIds") List<Long> seatIds);
}
