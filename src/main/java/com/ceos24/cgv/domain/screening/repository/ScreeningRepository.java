package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.movie.domain.Movie;
import com.ceos24.cgv.domain.screening.domain.Screening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    @Query("""
        SELECT DISTINCT s.movie
        FROM Screening s
        WHERE s.screen.theater.id = :theaterId
    """)
    List<Movie> findDistinctMoviesByTheaterId(@Param("theaterId") Long theaterId);

    @Query("""
            SELECT s
            FROM Screening s
            JOIN FETCH s.movie m
            JOIN FETCH s.screen sc
            WHERE sc.theater.id = :theaterId
            ORDER BY m.title, sc.name, s.startTime
        """)
    List<Screening> findAllByTheaterIdWithDetails(@Param("theaterId") Long theaterId);
}

