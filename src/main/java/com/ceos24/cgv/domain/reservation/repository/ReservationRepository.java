package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    // 취소 요청이 동시에 들어왔을 때 둘다 성공하지 않도록 예매 행도 비관적 락으로 !
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reservation
            FROM Reservation reservation
            WHERE reservation.id = :reservationId
            """)
    Optional<Reservation> findByIdForUpdate(
            @Param("reservationId")
            Long reservationId
    );
}