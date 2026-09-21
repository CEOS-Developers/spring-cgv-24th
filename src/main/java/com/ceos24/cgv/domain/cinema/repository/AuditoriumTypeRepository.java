package com.ceos24.cgv.domain.cinema.repository;

import com.ceos24.cgv.domain.cinema.entity.AuditoriumType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriumTypeRepository extends JpaRepository<AuditoriumType, Long> {

    boolean existsByName(String name);
}
