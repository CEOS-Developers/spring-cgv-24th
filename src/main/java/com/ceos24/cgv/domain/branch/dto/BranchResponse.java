package com.ceos24.cgv.domain.branch.dto;

import com.ceos24.cgv.domain.branch.entity.Branch;

public record BranchResponse(Long id, String name, String address) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getAddress());
    }
}
