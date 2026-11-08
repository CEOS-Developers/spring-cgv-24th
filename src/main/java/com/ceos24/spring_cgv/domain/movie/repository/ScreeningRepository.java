package com.ceos24.spring_cgv.domain.movie.repository;

import com.ceos24.spring_cgv.domain.movie.entity.Screening;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

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

    /**
     * 함수 기능: 금액 계산과 좌석 검증에 필요한 연관을 함께 조회한다.
     *          영화(제목), 상영관, 상영관 종류(좌석 배치, 추가 요금)를 한 번에 가져온다.
     * @param id 상영회차 식별자
     * @return 연관이 초기화된 상영회차
     */
    @EntityGraph(attributePaths = {"movie", "screen.screenType"})
    Optional<Screening> findWithDetailsById(Long id);
}