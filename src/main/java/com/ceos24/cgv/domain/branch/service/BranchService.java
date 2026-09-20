package com.ceos24.cgv.domain.branch.service;

import com.ceos24.cgv.domain.branch.dto.BranchDetailResponse;
import com.ceos24.cgv.domain.branch.dto.BranchResponse;
import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.Theater;
import com.ceos24.cgv.domain.branch.repository.BranchRepository;
import com.ceos24.cgv.domain.branch.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branchRepository;
    private final TheaterRepository theaterRepository;

    public List<BranchResponse> findAll() {
        return branchRepository.findAll().stream()
                .map(BranchResponse::from)
                .toList();
    }

    public BranchDetailResponse findById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.BRANCH_NOT_FOUND));
        List<Theater> theaters = theaterRepository.findByBranchId(id);
        return BranchDetailResponse.from(branch, theaters);
    }
}
