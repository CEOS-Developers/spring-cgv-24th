package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Screening;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    List<Screening> findByTheaterIdOrderByStartAtAsc(Long theaterId);

    @Query("""
            SELECT s
            FROM Screening s
            JOIN FETCH s.movie m
            JOIN FETCH s.theater t
            JOIN FETCH t.branch b
            JOIN FETCH t.theaterType tt
            WHERE (:movieId IS NULL OR m.id = :movieId)
              AND (:branchId IS NULL OR b.id = :branchId)
              AND (:startInclusive IS NULL OR s.startAt >= :startInclusive)
              AND (:endExclusive IS NULL OR s.startAt < :endExclusive)
            ORDER BY s.startAt ASC
            """)
    List<Screening> searchWithGraph(@Param("movieId") Long movieId,
                                    @Param("branchId") Long branchId,
                                    @Param("startInclusive") LocalDateTime startInclusive,
                                    @Param("endExclusive") LocalDateTime endExclusive);

    @Query("""
            SELECT s FROM Screening s
            JOIN FETCH s.theater t
            JOIN FETCH t.theaterType
            WHERE s.id = :id
            """)
    Optional<Screening> findByIdWithTheaterType(@Param("id") Long id);
}
