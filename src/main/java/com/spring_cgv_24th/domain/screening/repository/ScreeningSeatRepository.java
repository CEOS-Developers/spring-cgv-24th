package com.spring_cgv_24th.domain.screening.repository;

import com.spring_cgv_24th.domain.screening.entity.ScreeningSeat;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {

    List<ScreeningSeat> findAllByScreeningIdOrderByRowNoAscColumnNoAsc(Long screeningId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScreeningSeat s where s.id = :id and s.screening.id = :screeningId")
    Optional<ScreeningSeat> findByIdAndScreeningIdForUpdate(
            @Param("id") Long id, @Param("screeningId") Long screeningId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ScreeningSeat s where s.reservation.id = :reservationId order by s.id")
    List<ScreeningSeat> findAllByReservationIdForUpdate(@Param("reservationId") Long reservationId);
}
