package com.ceos24.cgv.domain.branch.dto;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.branch.entity.Theater;

import java.util.List;

public record BranchDetailResponse(
        Long id,
        String name,
        String address,
        Region region,
        String regionName,
        BranchStatus status,
        String statusName,
        String description,
        String imageUrl,
        List<TheaterSummary> theaters
) {
    public record TheaterSummary(Long id, String name) {

        public static TheaterSummary from(Theater theater) {
            return new TheaterSummary(theater.getId(), theater.getName());
        }
    }

    public static BranchDetailResponse from(Branch branch, List<Theater> theaters) {
        return new BranchDetailResponse(
                branch.getId(),
                branch.getName(),
                branch.getAddress(),
                branch.getRegion(),
                branch.getRegion().getDisplayName(),
                branch.getStatus(),
                branch.getStatus().getDisplayName(),
                branch.getDescription(),
                branch.getImageUrl(),
                theaters.stream().map(TheaterSummary::from).toList()
        );
    }
}
