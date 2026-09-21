package com.ceos24.cgv.domain.branch.controller;

import com.ceos24.cgv.domain.branch.dto.BranchDetailResponse;
import com.ceos24.cgv.domain.branch.dto.BranchResponse;
import com.ceos24.cgv.domain.branch.dto.RegionResponse;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.branch.service.BranchService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "지점", description = "지점 및 소속 상영관 조회")
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @Operation(summary = "지점 목록 조회 (지역 탭 필터 / 지역명·지점명 검색)")
    @GetMapping
    public ApiResponse<List<BranchResponse>> list(
            @Parameter(description = "지역 탭. 검색어가 있으면 무시된다")
            @RequestParam(required = false) Region region,
            @Parameter(description = "지역명 또는 지점명")
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(branchService.search(region, keyword));
    }

    @Operation(summary = "지역 목록 조회 (지역별 극장 수)")
    @GetMapping("/regions")
    public ApiResponse<List<RegionResponse>> regions() {
        return ApiResponse.success(branchService.findRegions());
    }

    @Operation(summary = "지점 단건 조회 (소속 상영관 포함)")
    @GetMapping("/{id}")
    public ApiResponse<BranchDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(branchService.findById(id));
    }
}
