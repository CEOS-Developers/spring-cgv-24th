package com.ceos24.spring_cgv.domain.movie.repository;

import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
}
