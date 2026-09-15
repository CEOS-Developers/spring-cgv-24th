package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.Screening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    @EntityGraph(attributePaths = {"movie", "screen"})
    List<Screening> findAllByMovie_IdOrderByStartAt(Long movieId);
}
