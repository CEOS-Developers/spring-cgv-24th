package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScreeningRepository
        extends JpaRepository<Screening, Long> {

    // 특정 영화의 상영회차 조회
    List<Screening> findByMovie_MovieId(Long movieId);

    // 특정 상영관의 상영회차 조회
    List<Screening> findByScreen_ScreenId(Long screenId);

    // 특정 영화 + 특정 상영관의 상영회차 조회
    List<Screening> findByMovie_MovieIdAndScreen_ScreenId(
            Long movieId,
            Long screenId
    );
}