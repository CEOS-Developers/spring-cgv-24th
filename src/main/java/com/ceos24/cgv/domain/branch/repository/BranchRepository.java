package com.ceos24.cgv.domain.branch.repository;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    // 제외 상태를 파라미터로 받는다. JPQL에 enum 리터럴을 쓰면 FQN을 매번 적어야 한다.
    @Query("SELECT b FROM Branch b WHERE b.status <> :excluded ORDER BY b.region, b.name")
    List<Branch> findAllListed(@Param("excluded") BranchStatus excluded);

    @Query("SELECT b FROM Branch b WHERE b.status <> :excluded AND b.region = :region ORDER BY b.name")
    List<Branch> findByRegion(@Param("region") Region region,
                              @Param("excluded") BranchStatus excluded);

    // 지역 표시명은 DB에 없으므로 키워드를 Region 목록으로 변환해 IN 절에 넘긴다.
    @Query("""
            SELECT b FROM Branch b
            WHERE b.status <> :excluded
              AND (b.name LIKE %:keyword% OR b.region IN :regions)
            ORDER BY b.region, b.name
            """)
    List<Branch> searchByKeyword(@Param("keyword") String keyword,
                                 @Param("regions") List<Region> regions,
                                 @Param("excluded") BranchStatus excluded);
}
