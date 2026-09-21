package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    @Query("SELECT rs FROM ReservationSeat rs JOIN FETCH rs.seat WHERE rs.reservation.id = :reservationId")
    List<ReservationSeat> findByReservationIdWithSeat(@Param("reservationId") Long reservationId);

    @Query("SELECT rs FROM ReservationSeat rs JOIN FETCH rs.seat WHERE rs.reservation.id IN :reservationIds")
    List<ReservationSeat> findByReservationIdInWithSeat(@Param("reservationIds") List<Long> reservationIds);

    List<ReservationSeat> findByScheduleIdAndSeatIdIn(Long scheduleId, List<Long> seatIds);

    void deleteByReservationId(Long reservationId);
}
