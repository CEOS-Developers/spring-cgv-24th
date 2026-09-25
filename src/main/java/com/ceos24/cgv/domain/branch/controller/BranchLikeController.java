package com.ceos24.cgv.domain.branch.controller;

import com.ceos24.cgv.domain.branch.dto.BranchLikeResponse;
import com.ceos24.cgv.domain.branch.service.BranchLikeService;
import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "극장 찜", description = "극장 찜 등록/해제/목록")
@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchLikeController {

    private final BranchLikeService branchLikeService;

    @Operation(summary = "극장 찜 (이미 찜한 경우에도 성공)")
    @PostMapping("/{branchId}/likes")
    public ApiResponse<Void> like(@AuthenticationPrincipal AuthUser authUser,
                                  @PathVariable Long branchId) {
        branchLikeService.like(branchId, authUser.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "극장 찜 해제 (찜하지 않은 경우에도 성공)")
    @DeleteMapping("/{branchId}/likes")
    public ApiResponse<Void> unlike(@AuthenticationPrincipal AuthUser authUser,
                                    @PathVariable Long branchId) {
        branchLikeService.unlike(branchId, authUser.userId());
        return ApiResponse.success();
    }

    @Operation(summary = "내가 찜한 극장 목록 (최근 찜한 순)")
    @GetMapping("/likes")
    public ApiResponse<List<BranchLikeResponse>> myLikes(
            @AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(branchLikeService.findMyLikes(authUser.userId()));
    }
}
