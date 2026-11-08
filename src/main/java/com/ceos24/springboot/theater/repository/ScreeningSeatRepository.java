package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.ScreeningSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ScreeningSeat ss
            where ss.screening.screeningId = :screeningId and ss.seat.seatId in :seatIds
            order by ss.seat.seatId
            """)
    List<ScreeningSeat> findAllWithLock(
            @Param("screeningId") Long screeningId,
            @Param("seatIds") List<Long> seatIds
    );
}
