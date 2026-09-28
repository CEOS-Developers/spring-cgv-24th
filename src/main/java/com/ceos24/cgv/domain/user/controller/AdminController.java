package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "관리자 API")
public class AdminController {

    @Operation(summary = "관리자 권한 확인")
    @GetMapping("/check")
    public ApiResponse<Void> check() {
        return ApiResponse.onSuccess("관리자 인증에 성공했습니다.");
    }
}