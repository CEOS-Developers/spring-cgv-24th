package com.ceos24.cgv.repository;

import com.ceos24.cgv.domain.BranchLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchLikeRepository extends JpaRepository<BranchLike, Long> {
    boolean existsByUserIdAndBranchId(Long userId, Long branchId);
}
