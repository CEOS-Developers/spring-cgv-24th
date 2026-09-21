package com.spring_cgv_24th.domain.screening.repository;

import com.spring_cgv_24th.domain.screening.entity.Screening;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    @Query("""
            select (count(s) > 0) from Screening s
            where s.auditorium.id = :auditoriumId
              and s.startsAt < :endsAt
              and s.endsAt > :startsAt
            """)
    boolean existsOverlapping(
            @Param("auditoriumId") Long auditoriumId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt);

    @EntityGraph(attributePaths = {"movie", "auditorium", "auditorium.theater", "auditorium.type"})
    @Query("select s from Screening s where s.id = :id")
    Optional<Screening> findDetailsById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"movie", "auditorium", "auditorium.theater", "auditorium.type"})
    List<Screening> findAllByOrderByStartsAtAscIdAsc();
}
