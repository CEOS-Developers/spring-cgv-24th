package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.screening.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    boolean existsByAuditorium_IdAndStartsAtLessThanAndEndsAtGreaterThan(
            Long auditoriumId,
            LocalDateTime newEndsAt,
            LocalDateTime newStartsAt
    );

    List<Screening> findAllByAuditorium_Cinema_IdOrderByStartsAtAsc(Long cinemaId);
}
