package com.ceos24.spring_cgv.domain.reservation.repository;

import com.ceos24.spring_cgv.domain.reservation.entity.ReservationSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    /**
     * 함수 기능: 주어진 좌석 중 현재 점유 중인 좌석이 있는지 확인한다.
     *          점유가 풀린 좌석은 키가 null이므로 조회되지 않는다.
     * @param activeSeatKeys 확인할 좌석들의 점유 키
     * @return 하나라도 점유 중이면 true
     */
    boolean existsByActiveSeatKeyIn(Collection<String> activeSeatKeys);

    /**
     * 함수 기능: 해당 예매가 현재 점유하고 있는 좌석 행을 조회한다.
     *          좌석 행이 다른 예매에 넘어갔다면 그 좌석은 조회되지 않는다.
     * @param reservationId 예매 식별자
     * @return 좌석 행 목록
     */
    List<ReservationSeat> findAllByReservationId(Long reservationId);
}
