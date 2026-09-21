package com.ceos.cgv.domain.movie.repository;

import com.ceos.cgv.domain.movie.entity.ScreeningSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningSeatRepository extends JpaRepository<ScreeningSeat, Long> {
}
