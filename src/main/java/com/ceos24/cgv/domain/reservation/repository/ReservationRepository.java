package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query(
            """
        SELECT DISTINCT r
        FROM Reservation r
        LEFT JOIN FETCH r.seatReservations sr
        LEFT JOIN FETCH sr.seat
        WHERE r.id = :reservationId AND r.member.id = :memberId
    """)
    Optional<Reservation> findByIdAndMemberIdWithSeats(
            @Param("reservationId") Long reservationId, @Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :reservationId")
    Optional<Reservation> findByIdWithLock(@Param("reservationId") Long reservationId);

    @Query(
            """
        SELECT DISTINCT r
        FROM Reservation r
        LEFT JOIN FETCH r.seatReservations
        WHERE r.id IN :reservationIds
    """)
    List<Reservation> findAllWithSeatsByIdIn(@Param("reservationIds") List<Long> reservationIds);

    @Query(
            """
        SELECT COUNT(r)
        FROM Reservation r
        WHERE r.member.id = :memberId AND r.status = :status AND r.expiresAt > :now
    """)
    long countActiveHolds(
            @Param("memberId") Long memberId,
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now);

    @Query(
            """
        SELECT r.id
        FROM Reservation r
        WHERE r.status = :status AND r.expiresAt <= :now
        ORDER BY r.id ASC
    """)
    List<Long> findAllExpiredHoldReservationIds(
            @Param("status") ReservationStatus status, @Param("now") LocalDateTime now);
}
