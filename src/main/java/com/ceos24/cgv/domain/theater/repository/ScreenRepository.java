package com.ceos24.cgv.domain.theater.repository;

import com.ceos24.cgv.domain.theater.entity.Screen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ScreenRepository extends JpaRepository<Screen, Long> {

    @Query("""
            SELECT screen
            FROM Screen screen
            JOIN FETCH screen.screenType
            WHERE screen.theater.id = :theaterId
            ORDER BY screen.id ASC
            """)
    List<Screen> findAllByTheaterIdWithScreenType(
            @Param("theaterId") Long theaterId
    );
}