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

    // theaterType이 enum으로 바뀌어 Theater에 내장되므로 별도 fetch join이 불필요하다.
    @Query("""
            SELECT s FROM Screening s
            JOIN FETCH s.theater t
            WHERE s.id = :id
            """)
    Optional<Screening> findByIdWithTheaterType(@Param("id") Long id);
}
