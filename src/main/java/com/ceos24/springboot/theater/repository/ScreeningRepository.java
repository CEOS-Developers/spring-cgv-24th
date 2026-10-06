package com.ceos24.springboot.theater.repository;

import com.ceos24.springboot.theater.domain.Screening;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

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

    // 특정 영화관의 모든 상영회차 조회
    List<Screening> findByScreen_Theater_TheaterId(Long theaterId);

//    비관적 락 설정
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Screening s
            where s.screeningId = :screeningId
            """)
    Optional<Screening> findByIdWithLock(
            @Param("screeningId") Long screeningId
    );


}