package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Reservation;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            SELECT DISTINCT r FROM Reservation r
            JOIN FETCH r.user
            JOIN FETCH r.screening s
            JOIN FETCH s.movie
            JOIN FETCH s.theater t
            JOIN FETCH t.branch
            LEFT JOIN FETCH r.seats
            WHERE r.id = :id
            """)
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);
}
