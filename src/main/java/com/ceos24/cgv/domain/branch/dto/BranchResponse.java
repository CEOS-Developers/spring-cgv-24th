package com.ceos24.cgv.domain.branch.dto;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;

import java.util.List;

// description은 의도적으로 제외한다. 목록에 수천 자짜리 안내문을 지점 수만큼 실을 이유가 없다.
public record BranchResponse(
        Long id,
        String name,
        String address,
        Region region,
        String regionName,
        BranchStatus status,
        String statusName,
        String imageUrl,
        List<String> specialTypes
) {
    public static BranchResponse from(Branch branch, List<String> specialTypes) {
        return new BranchResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getRegion(),
                branch.getRegion().getDisplayName(),
                branch.getStatus(),
                branch.getStatus().getDisplayName(),
                branch.getImageUrl(),
                specialTypes
        );
    }
}
