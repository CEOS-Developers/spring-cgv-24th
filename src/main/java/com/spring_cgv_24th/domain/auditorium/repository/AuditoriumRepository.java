package com.spring_cgv_24th.domain.auditorium.repository;

import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {

    @EntityGraph(attributePaths = "type")
    List<Auditorium> findAllByTheaterIdOrderByIdAsc(Long theaterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auditorium a where a.id = :id")
    Optional<Auditorium> findByIdForUpdate(@Param("id") Long id);
}
