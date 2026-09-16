package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.screening.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository
        extends JpaRepository<Screening, Long> {
}