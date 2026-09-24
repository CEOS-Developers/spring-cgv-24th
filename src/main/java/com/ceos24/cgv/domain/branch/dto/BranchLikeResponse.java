package com.ceos24.cgv.domain.branch.dto;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchLike;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;

import java.time.LocalDateTime;

// status를 싣는 이유: 찜한 뒤 폐관된 극장을 목록에서 조용히 빼면 사용자는 왜 사라졌는지 모른다.
public record BranchLikeResponse(
        Long branchId,
        String name,
        Region region,
        String regionName,
        BranchStatus status,
        String statusName,
        LocalDateTime likedAt
) {
    public static BranchLikeResponse from(BranchLike like) {
        Branch branch = like.getBranch();
        return new BranchLikeResponse(
                branch.getId(),
                branch.getName(),
                branch.getRegion(),
                branch.getRegion().getDisplayName(),
                branch.getStatus(),
                branch.getStatus().getDisplayName(),
                like.getCreatedAt()
        );
    }
}
