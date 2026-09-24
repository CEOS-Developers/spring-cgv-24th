package com.ceos.cgv.domain.reservation.repository;

import com.ceos.cgv.domain.reservation.dto.ReservationSnapshot;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @EntityGraph(attributePaths = "reservedSeats")
    Optional<Reservation> findWithSeatsById(Long reservationId);

    @EntityGraph(attributePaths = "reservedSeats")
    Optional<Reservation> findByUser_IdAndRequestKey(Long userId, String requestKey);

    @Query("""
            select new com.ceos.cgv.domain.reservation.dto.ReservationSnapshot(
                r.user.id, r.screening.id, r.screening.movie.id, r.status, r.expiresAt)
            from Reservation r where r.id = :reservationId
            """)
    Optional<ReservationSnapshot> findSnapshotById(@Param("reservationId") Long reservationId);

    @Query("""
            select r.id from Reservation r
            where r.status = com.ceos.cgv.domain.reservation.enums.ReservationStatus.HELD
              and r.expiresAt <= :now
            order by r.expiresAt, r.id
            """)
    List<Long> findExpiredHoldIds(@Param("now") Instant now,
                                  Pageable pageable);

    @Query("""
            select count(r) from Reservation r
            where r.user.id = :userId and r.status = :status and r.expiresAt > :now
            """)
    long countActiveHolds(@Param("userId") Long userId,
                          @Param("status") ReservationStatus status,
                          @Param("now") Instant now);
}
