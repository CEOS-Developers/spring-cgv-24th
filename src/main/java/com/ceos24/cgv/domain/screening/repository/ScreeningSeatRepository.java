package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreeningSeatRepository
        extends JpaRepository<ScreeningSeat, Long> {

    // 상영 일정과 좌석 조합에 락을 걸어
    // 다른 상영 일정의 동일한 물리 좌석 예매와 경합하지 않도록 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT screeningSeat
            FROM ScreeningSeat screeningSeat
            WHERE screeningSeat.screening.id = :screeningId
              AND screeningSeat.seat.id IN :seatIds
            ORDER BY screeningSeat.id
            """)
    List<ScreeningSeat> findAllForUpdate(
            @Param("screeningId") Long screeningId,
            @Param("seatIds") List<Long> seatIds
    );
}