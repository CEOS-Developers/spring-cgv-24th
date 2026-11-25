package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.screening.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    boolean existsByAuditorium_IdAndStartsAtLessThanAndEndsAtGreaterThan(
            Long auditoriumId,
            LocalDateTime newEndsAt,
            LocalDateTime newStartsAt
    );

    // 응답에 필요한 영화·상영관·영화관을 함께 로딩한다.
    @Query("""
            select s
            from Screening s
            join fetch s.movie
            join fetch s.auditorium a
            join fetch a.cinema c
            where c.id = :cinemaId
            order by s.startsAt asc
            """)
    List<Screening> findAllByAuditorium_Cinema_IdOrderByStartsAtAsc(@Param("cinemaId") Long cinemaId);
}
