package com.ceos24.cgv.domain.branch.repository;

import com.ceos24.cgv.domain.branch.entity.BranchLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchLikeRepository extends JpaRepository<BranchLike, Long> {
    boolean existsByUserIdAndBranchId(Long userId, Long branchId);

    @Query("""
            SELECT bl FROM BranchLike bl
            JOIN FETCH bl.branch
            WHERE bl.user.id = :userId
            ORDER BY bl.id DESC
            """)
    List<BranchLike> findAllByUserIdWithBranch(@Param("userId") Long userId);

    // 파생 deleteBy는 SELECT 후 엔티티별로 지워서, 동시 해제로 이미 사라진 행을 만나면
    // 낙관적 락 예외가 난다. 벌크 DELETE는 0행이어도 정상이라 해제가 멱등해진다.
    @Modifying
    @Query("DELETE FROM BranchLike bl WHERE bl.user.id = :userId AND bl.branch.id = :branchId")
    int deleteByUserIdAndBranchId(@Param("userId") Long userId, @Param("branchId") Long branchId);
}
