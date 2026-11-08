package com.spring_cgv_24th.domain.reservation.repository;

import com.spring_cgv_24th.domain.reservation.entity.Reservation;
import com.spring_cgv_24th.domain.reservation.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where (r.status = :pending and r.expiresAt <= :now) "
            + "or r.status = :failed order by r.id")
    List<Reservation> findForSeatReleaseForUpdate(
            @Param("pending") ReservationStatus pending, @Param("failed") ReservationStatus failed,
            @Param("now") LocalDateTime now);
}
