package com.ceos24.springboot.reservation.repository;

import com.ceos24.springboot.reservation.domain.Reservation;
import com.ceos24.springboot.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;

import java.util.Optional;
import java.time.LocalDateTime;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long> {

    // 확정, 취소, 만료 처리는 동일한 예매 행을 잠가 직렬화.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.reservationId = :reservationId")
    Optional<Reservation> findByIdWithLock(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from Reservation r
            where r.reservationId = :reservationId and r.user.id = :userId
            """)
    Optional<Reservation> findMyReservationWithLock(
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId
    );

    // 결제 진행 중/결과 미확정 예매는 일반 만료 대신 PaymentRecoveryScheduler가 처리한다.
    @Query("""
            select r.reservationId from Reservation r
            where r.status = :status and r.expiresAt <= :now and r.paymentProcessing = false
            order by r.reservationId
            """)
    List<Long> findExpiredReservationIds(
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select r.reservationId from Reservation r
            where r.user.id = :userId and r.status = :status and r.expiresAt <= :now and r.paymentProcessing = false
            order by r.reservationId
            """)
    List<Long> findExpiredReservationIdsByUser(
            @Param("userId") Long userId,
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now
    );

    Optional<Reservation> findByReservationIdAndUser_Id(
            Long reservationId,
            Long userId
    );

    // 최근 예메부터 정렬하여 로그인 ID와 예매자의 ID 일치하는 예매만 조회
    @EntityGraph(attributePaths = "screening")
    List<Reservation> findAllByUser_IdOrderByReservationAtDesc(
            Long userId
    );
}
