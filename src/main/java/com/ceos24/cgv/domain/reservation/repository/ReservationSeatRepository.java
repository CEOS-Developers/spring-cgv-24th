package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

// 점유 판정 조건은 두 줄이다. 취소·만료로 풀린 행은 release_key가 0이 아니라 이미 빠지고,
// 남는 예외가 만료 시각은 지났지만 아직 정리되지 않은 선점이라 시각 조건을 더한다.
public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    @Query("""
            SELECT rs.screening.id AS screeningId, COUNT(rs) AS reservedCount
            FROM ReservationSeat rs
            JOIN rs.reservation r
            WHERE rs.screening.id IN :screeningIds
              AND rs.releaseKey = 0
              AND (r.status <> :pending OR r.expiresAt > :now)
            GROUP BY rs.screening.id
            """)
    List<SeatCountProjection> countOccupiedByScreeningIds(@Param("screeningIds") List<Long> screeningIds,
                                                          @Param("pending") ReservationStatus pending,
                                                          @Param("now") LocalDateTime now);

    interface SeatCountProjection {
        Long getScreeningId();
        Long getReservedCount();
    }

    @Query("""
            SELECT rs.rowNum AS rowNum, rs.colNum AS colNum
            FROM ReservationSeat rs
            JOIN rs.reservation r
            WHERE rs.screening.id = :screeningId
              AND rs.releaseKey = 0
              AND (r.status <> :pending OR r.expiresAt > :now)
            ORDER BY rs.rowNum, rs.colNum
            """)
    List<SeatPositionProjection> findOccupiedPositionsByScreeningId(@Param("screeningId") Long screeningId,
                                                                    @Param("pending") ReservationStatus pending,
                                                                    @Param("now") LocalDateTime now);

    interface SeatPositionProjection {
        int getRowNum();
        int getColNum();
    }
}
