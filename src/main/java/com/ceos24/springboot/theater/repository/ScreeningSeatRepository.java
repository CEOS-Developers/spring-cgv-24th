package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.ScreeningSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ss from ScreeningSeat ss
            where ss.screening.screeningId = :screeningId and ss.seat.seatId = :seatId
            """)
    Optional<ScreeningSeat> findWithLock(@Param("screeningId") Long screeningId, @Param("seatId") Long seatId);
}
