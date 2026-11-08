package com.ceos24.springboot.reservation.repository;

import com.ceos24.springboot.reservation.domain.ReservationSeat;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

//  하나의 예매에 포함된 좌석들을 조회
    List<ReservationSeat> findAllByReservation_ReservationId(Long reservationId);

//  중복 좌석 검사
    List<ReservationSeat> findAllByScreening_ScreeningIdAndSeat_SeatIdIn(
            Long screeningId,
            List<Long> seatIds
    );

//  영화 예매 취소할 때 예약된 좌석 삭제
    @Modifying
    @Query("delete from ReservationSeat rs where rs.reservation.reservationId = :reservationId")
    void deleteAllByReservation_ReservationId(@Param("reservationId") Long reservationId);

    @Query("""
            select distinct r.reservationId from ReservationSeat rs
            join rs.reservation r
            where rs.screening.screeningId = :screeningId
              and rs.seat.seatId in :seatIds
              and r.status = :status and r.expiresAt <= :now
              and r.paymentProcessing = false
            order by r.reservationId
            """)
    List<Long> findExpiredReservationIdsBySeats(
            @Param("screeningId") Long screeningId,
            @Param("seatIds") List<Long> seatIds,
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now
    );

    //본인의 예매 ID목록에 해당하는 좌석만 일괄 조회
    @EntityGraph(attributePaths = "seat")
    List<ReservationSeat> findAllByReservation_ReservationIdIn(
            List<Long> reservationIds
    );

}
