package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
    List<ScreeningSeat> findAllByScreening_Id(Long screeningId);
}
