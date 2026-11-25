package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.dto.SeatOccupancy;
import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    List<ScreeningSeat> findAllByScreening_Id(Long screeningId);

    @Query("""
            select new com.ceos.cgv.domain.movie.dto.SeatOccupancy(
                ss.seatRow, ss.seatNumber, r.status, r.expiresAt)
            from ScreeningSeat ss
            left join ss.currentReservation r
            where ss.screening.id = :screeningId
            order by ss.seatRow, ss.seatNumber
            """)
    List<SeatOccupancy> findSeatOccupanciesByScreeningId(@Param("screeningId") Long screeningId);

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
