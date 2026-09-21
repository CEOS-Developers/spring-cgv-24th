package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// 같은 좌석에 동시 예매 요청 방지 -> 비관적 락
public interface SeatRepository
        extends JpaRepository<Seat, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT seat
            FROM Seat seat
            JOIN FETCH seat.screen
            WHERE seat.id IN :seatIds
            ORDER BY seat.id ASC
            """)
    List<Seat> findAllByIdForUpdate(
            @Param("seatIds")
            List<Long> seatIds
    );
}