package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.dto.LoginRequest;
import com.ceos24.cgv.domain.user.dto.LoginResponse;
import com.ceos24.cgv.domain.user.dto.SignupRequest;
import com.ceos24.cgv.domain.user.dto.SignupResponse;
import com.ceos24.cgv.domain.user.dto.TokenReissueRequest;
import com.ceos24.cgv.domain.user.dto.TokenReissueResponse;
import com.ceos24.cgv.domain.user.service.AuthService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입 / 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입 — 권한은 항상 USER로 생성")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        return ApiResponse.success(authService.signup(req));
    }

    @Operation(summary = "로그인 — Access Token 발급. 계정 없음과 비밀번호 불일치는 같은 응답")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(authService.login(req));
    }

    @Operation(summary = "액세스 토큰 재발급 — 본문의 리프레시 토큰으로. 리프레시 토큰은 그대로 유지")
    @PostMapping("/reissue")
    public ApiResponse<TokenReissueResponse> reissue(@Valid @RequestBody TokenReissueRequest req) {
        return ApiResponse.success(authService.reissue(req));
    }
}
