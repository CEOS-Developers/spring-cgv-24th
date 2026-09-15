package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    // 해당 회차에서 특정 상태인 좌석 ID만 가져옴
    @Query("""
            select rs.seat.id
            from ReservationSeat rs
            where rs.reservation.screening.id = :screeningId
              and rs.reservation.status = :status
            """)
    List<Long> findSeatIdsByScreeningIdAndStatus(
            @Param("screeningId") Long screeningId,
            @Param("status") ReservationStatus status
    );

    List<ReservationSeat> findAllByReservationIdOrderBySeatRowNumberAscSeatColumnNumberAsc(
            Long reservationId
    );
}
