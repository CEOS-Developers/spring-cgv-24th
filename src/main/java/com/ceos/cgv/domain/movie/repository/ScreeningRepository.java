package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.Screening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Screening s where s.id = :screeningId")
    java.util.Optional<Screening> findByIdWithLock(@Param("screeningId") Long screeningId);

    @EntityGraph(attributePaths = {"movie", "screen"})
    List<Screening> findAllByMovie_IdOrderByStartAt(Long movieId);
}
