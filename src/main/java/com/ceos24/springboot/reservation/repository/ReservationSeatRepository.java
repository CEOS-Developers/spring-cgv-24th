package com.ceos24.springboot.reservation.repository;

import com.ceos24.springboot.reservation.domain.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

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
    void deleteAllByReservation_ReservationId(Long reservationId);
}