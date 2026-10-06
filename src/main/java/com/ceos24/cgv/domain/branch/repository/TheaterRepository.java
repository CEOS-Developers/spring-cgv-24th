package com.ceos24.cgv.domain.branch.repository;

import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TheaterRepository extends JpaRepository<Theater, Long> {

    List<Theater> findByBranchId(Long branchId);

    interface BranchTheaterType {
        Long getBranchId();
        TheaterType getTheaterType();
    }

    // 특별관 라벨은 지점 컬럼이 아니라 보유 상영관의 타입을 집계한 값이다.
    // 지점마다 조회하면 N+1이므로 IN + GROUP BY로 한 번에 가져온다.
    @Query("""
            SELECT t.branch.id AS branchId, t.theaterType AS theaterType
            FROM Theater t
            WHERE t.branch.id IN :branchIds
            GROUP BY t.branch.id, t.theaterType
            """)
    List<BranchTheaterType> findTheaterTypesByBranchIds(@Param("branchIds") List<Long> branchIds);
}
