package com.ceos24.spring_cgv.domain.movie.repository;

import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    /**
     * 함수 기능: 상영관에 새로운 영화를 등록할 때, 기존 상영시간과 겹치는 지 판단한다.
     *          겹치지 않는 조건 = (기존.startTime >= 신규.endTime) OR (기존.endTime <= 신규.startTime)
     *          겹치는 조건 = (기존.startTime < 신규.endTime) AND (기존.endTime > 신규.startTime)
     * @param screenId 상영관ID
     * @param newEndTime 신규 영화 endTime
     * @param newStartTime 신규 영화 startTime
     * @return t = 중복 / f = 중복 x
     */
    boolean existsByScreenIdAndStartTimeLessThanAndEndTimeGreaterThan(Long screenId, LocalDateTime newEndTime, LocalDateTime newStartTime);
}