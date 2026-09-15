package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findAllByAuditoriumIdOrderByRowNumberAscColumnNumberAsc(
            Long auditoriumId
    );

    // todo: Lock 어노테이션 학습필요
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :seatIds order by s.id")
    List<Seat> findAllByIdInForUpdate(@Param("seatIds") List<Long> seatIds);
}
