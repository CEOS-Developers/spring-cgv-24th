package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.ReservationSeat;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {
    List<ReservationSeat> findByScreeningId(Long screeningId);

    @Query("""
            SELECT rs.screening.id AS screeningId, COUNT(rs) AS reservedCount
            FROM ReservationSeat rs
            WHERE rs.screening.id IN :screeningIds
            GROUP BY rs.screening.id
            """)
    List<SeatCountProjection> countGroupedByScreeningIds(@Param("screeningIds") List<Long> screeningIds);

    interface SeatCountProjection {
        Long getScreeningId();
        Long getReservedCount();
    }

    @Query("""
            SELECT rs.rowNum AS rowNum, rs.colNum AS colNum
            FROM ReservationSeat rs
            WHERE rs.screening.id = :screeningId
            ORDER BY rs.rowNum, rs.colNum
            """)
    List<SeatPositionProjection> findPositionsByScreeningId(@Param("screeningId") Long screeningId);

    interface SeatPositionProjection {
        int getRowNum();
        int getColNum();
    }
}
