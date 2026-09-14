package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {
    List<Screening> findByTheaterIdOrderByStartAtAsc(Long theaterId);
}
