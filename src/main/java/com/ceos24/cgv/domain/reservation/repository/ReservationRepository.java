package com.ceos24.cgv.domain.reservation.repository;

import com.ceos24.cgv.domain.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.schedule s " +
            "JOIN FETCH s.movie " +
            "JOIN FETCH s.screen sc " +
            "JOIN FETCH sc.theater " +
            "WHERE r.id = :id")
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT r FROM Reservation r " +
            "JOIN FETCH r.schedule s " +
            "JOIN FETCH s.movie " +
            "JOIN FETCH s.screen sc " +
            "JOIN FETCH sc.theater " +
            "WHERE r.user.id = :userId " +
            "ORDER BY r.reservedAt DESC")
    List<Reservation> findByUserIdWithDetailsOrderByReservedAtDesc(@Param("userId") Long userId);
}
