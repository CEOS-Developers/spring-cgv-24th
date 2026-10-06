package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    List<ScreeningSeat> findAllByScreening_Id(Long screeningId);

    @EntityGraph(attributePaths = "currentReservation")
    List<ScreeningSeat> findAllByScreening_IdOrderBySeatRowAscSeatNumberAsc(Long screeningId);

    long countByScreening_Id(Long screeningId);

    @Query("""
            select ss.currentReservation.id from ScreeningSeat ss
            where ss.screening.id = :screeningId and ss.seatRow = :seatRow
              and ss.seatNumber = :seatNumber
            """)
    Optional<Long> findCurrentReservationId(
            @Param("screeningId") Long screeningId,
            @Param("seatRow") String seatRow,
            @Param("seatNumber") int seatNumber);

    @Query(value = """
            SELECT * FROM screening_seats
            WHERE screening_id = :screeningId AND seat_row = :seatRow AND seat_number = :seatNumber
            FOR UPDATE NOWAIT
            """, nativeQuery = true)
    Optional<ScreeningSeat> lockCoordinateNowait(
            @Param("screeningId") Long screeningId,
            @Param("seatRow") String seatRow,
            @Param("seatNumber") int seatNumber);
}
