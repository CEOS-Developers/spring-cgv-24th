package com.spring_cgv_24th.domain.auditorium.repository;

import com.spring_cgv_24th.domain.auditorium.entity.Auditorium;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumRepository extends JpaRepository<Auditorium, Long> {

    @EntityGraph(attributePaths = "type")
    List<Auditorium> findAllByTheaterIdOrderByIdAsc(Long theaterId);
}
