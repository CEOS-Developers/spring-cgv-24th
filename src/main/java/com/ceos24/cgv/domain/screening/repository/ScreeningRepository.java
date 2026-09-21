package com.ceos24.cgv.domain.screening.repository;

import com.ceos24.cgv.domain.branch.entity.TheaterType;
import com.ceos24.cgv.domain.screening.entity.Screening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    // 지점 정렬은 화면과 같이 이름 오름차순이다. 상영관 종류는 STRING으로 저장돼 DB가
    // 정렬하면 알파벳순이 되므로, 선언 순서를 지키려고 서비스에서 묶는다.
    @Query("""
            SELECT s
            FROM Screening s
            JOIN FETCH s.movie m
            JOIN FETCH s.theater t
            JOIN FETCH t.branch b
            WHERE s.startAt >= :startInclusive
              AND s.startAt < :endExclusive
              AND (:movieId IS NULL OR m.id = :movieId)
              AND (:filterByBranch = FALSE OR b.id IN :branchIds)
              AND (:theaterType IS NULL OR t.theaterType = :theaterType)
            ORDER BY b.name ASC, s.startAt ASC
            """)
    List<Screening> search(@Param("movieId") Long movieId,
                           @Param("filterByBranch") boolean filterByBranch,
                           @Param("branchIds") List<Long> branchIds,
                           @Param("theaterType") TheaterType theaterType,
                           @Param("startInclusive") LocalDateTime startInclusive,
                           @Param("endExclusive") LocalDateTime endExclusive);

    // 좌석 배치는 theaterType enum이 들고 있어 theater까지만 있으면 된다.
    @Query("""
            SELECT s FROM Screening s
            JOIN FETCH s.theater
            WHERE s.id = :id
            """)
    Optional<Screening> findByIdWithTheater(@Param("id") Long id);

    // 선점 응답은 영화 제목과 지점 이름까지 내려준다. 여기서 함께 가져오지 않으면
    // DTO 변환 시점에 LAZY 초기화 SELECT가 두 건 더 나간다.
    @Query("""
            SELECT s FROM Screening s
            JOIN FETCH s.movie
            JOIN FETCH s.theater t
            JOIN FETCH t.branch
            WHERE s.id = :id
            """)
    Optional<Screening> findByIdWithDetails(@Param("id") Long id);
}
