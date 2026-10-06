package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.dto.AdminCheckResponse;
import com.ceos24.cgv.global.response.ApiResponse;
import com.ceos24.cgv.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 권한 검사는 SecurityConfig의 /api/admin/** 규칙이 한다. 여기에 다시 검사를 두면 규칙이 두 곳으로 갈린다.
@Tag(name = "관리자", description = "관리자 전용")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Operation(summary = "관리자 권한 확인 — USER 403 / ADMIN 200")
    @GetMapping("/check")
    public ApiResponse<AdminCheckResponse> check(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.success(AdminCheckResponse.from(authUser));
    }
}
