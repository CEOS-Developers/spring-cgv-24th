package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

//  예매할 좌석이 실제 그 상영관에 속하는 좌석인지 검증
    List<Seat> findAllBySeatIdInAndScreenId_ScreenId(
            List<Long> seatIds,
            Long screenId
    );
}