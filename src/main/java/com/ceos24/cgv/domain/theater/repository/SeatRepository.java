package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Seat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScreenIdOrderBySeatNumberAsc(Long screenId);

    List<Seat> findAllByScreenIdAndSeatNumberInOrderBySeatNumberAsc(Long screenId, List<Long> seatNumbers);
}
