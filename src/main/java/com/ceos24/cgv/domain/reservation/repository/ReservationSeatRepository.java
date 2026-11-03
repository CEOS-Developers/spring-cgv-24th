package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    // 결제 대기와 확정 예매 모두 좌석을 점유한다. 만료 시각만으로 미확정 결제의 좌석을 해제하지 않는다.
    @Query("""
            select rs.seat.id
            from ReservationSeat rs
            where rs.reservation.screening.id = :screeningId
              and rs.reservation.status in :statuses
            """)
    List<Long> findSeatIdsByScreeningIdAndStatuses(
            @Param("screeningId") Long screeningId,
            @Param("statuses") List<ReservationStatus> statuses
    );

    List<ReservationSeat> findAllByReservationIdOrderBySeatRowNumberAscSeatColumnNumberAsc(
            Long reservationId
    );
}
