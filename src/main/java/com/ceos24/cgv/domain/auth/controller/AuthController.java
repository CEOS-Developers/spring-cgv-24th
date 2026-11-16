package com.ceos24.cgv.domain.auth.controller;

import com.ceos24.cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.cgv.domain.auth.dto.response.TokenResponse;
import com.ceos24.cgv.domain.auth.service.AuthService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.global.security.ApiUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입 및 로그인 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<Void>> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하여 Access Token을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token을 헤더에 담아 새로운 Access Token을 발급받습니다.")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(@RequestHeader("Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(ApiResponse.success(authService.reissue(refreshToken)));
    }

    @Operation(summary = "로그아웃", description = "Refresh Token을 폐기합니다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal ApiUserDetails userDetails, @RequestHeader("Refresh-Token") String refreshToken) {
        if (userDetails == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED_USER);
        }
        authService.logout(userDetails.memberId(), refreshToken);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
