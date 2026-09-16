package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.domain.Screen;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRepository extends JpaRepository<Screen, Long> {
    List<Screen> findByTheaterId(Long theaterId);
}
