package com.ceos24.cgv.dto.response;

import com.ceos24.cgv.domain.Branch;
import com.ceos24.cgv.domain.Theater;

import java.util.List;

public record BranchDetailResponse(Long id, String name, String address, List<TheaterSummary> theaters) {

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
                theaters.stream().map(TheaterSummary::from).toList()
        );
    }
}
