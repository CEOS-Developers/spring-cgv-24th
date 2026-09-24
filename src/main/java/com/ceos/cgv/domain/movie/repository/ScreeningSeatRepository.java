package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    List<ScreeningSeat> findAllByScreening_Id(Long screeningId);

    long countByScreening_Id(Long screeningId);

    @Query(value = """
            SELECT * FROM screening_seats
            WHERE screening_id = :screeningId AND seat_row = :seatRow AND seat_number = :seatNumber
            FOR UPDATE NOWAIT
            """, nativeQuery = true)
    Optional<ScreeningSeat> lockCoordinateNowait(
            @Param("screeningId") Long screeningId,
            @Param("seatRow") String seatRow,
            @Param("seatNumber") int seatNumber);
}
