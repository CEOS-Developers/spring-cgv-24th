package com.ceos24.spring_cgv.domain.reservation.repository;

import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    /**
     * 함수 기능: 해당 상영회차의 좌석 행을 모두 조회한다. 점유가 풀린(UNRESERVED) 행도 포함한다.
     * @param screeningId 상영회차 식별자
     * @return 좌석 행 목록
     */
    List<ReservationSeat> findAllByScreeningId(Long screeningId);

    /**
     * 함수 기능: 해당 예매가 현재 점유하고 있는 좌석 행을 조회한다.
     *          좌석 행이 다른 예매에 넘어갔다면 그 좌석은 조회되지 않는다.
     * @param reservationId 예매 식별자
     * @return 좌석 행 목록
     */
    List<ReservationSeat> findAllByReservationId(Long reservationId);
}
