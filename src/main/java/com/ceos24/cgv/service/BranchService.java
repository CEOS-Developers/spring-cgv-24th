package com.ceos24.cgv.service;

import com.ceos24.cgv.domain.Branch;
import com.ceos24.cgv.domain.Theater;
import com.ceos24.cgv.dto.response.BranchDetailResponse;
import com.ceos24.cgv.dto.response.BranchResponse;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.repository.BranchRepository;
import com.ceos24.cgv.repository.TheaterRepository;
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
