package com.ceos24.spring_cgv.domain.auth.controller;

import com.ceos24.spring_cgv.domain.auth.dto.request.LoginRequest;
import com.ceos24.spring_cgv.domain.auth.dto.request.SignUpRequest;
import com.ceos24.spring_cgv.domain.auth.dto.response.LoginResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.ReissueResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.SignUpResponse;
import com.ceos24.spring_cgv.domain.auth.dto.response.TokenIssueResult;
import com.ceos24.spring_cgv.domain.auth.exception.code.AuthSuccessCode;
import com.ceos24.spring_cgv.domain.auth.service.AuthService;
import com.ceos24.spring_cgv.global.apipayload.ApiResponse;
import com.ceos24.spring_cgv.global.security.userdetails.CustomUserDetails;
import com.ceos24.spring_cgv.global.security.util.CookieUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "인증 관련 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @SecurityRequirements
    @Operation(summary = "회원가입", description = "새로운 회원가입을 진행합니다.")
    @PostMapping("/signup")
    public ApiResponse<SignUpResponse> signUp(
            @Valid @RequestBody SignUpRequest request
            ){
        return ApiResponse.onSuccess(AuthSuccessCode.SIGNUP_OK, authService.signUp(request));
    }

    @SecurityRequirements
    @Operation(summary = "로그인", description = "로그인을 진행합니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
            ){

        TokenIssueResult<LoginResponse> result = authService.login(request);
        cookieUtil.setRtCookie(response, result.refreshToken());

        return ApiResponse.onSuccess(AuthSuccessCode.LOGIN_OK, result.body());
    }

    @SecurityRequirements
    @Operation(summary = "토큰 재발급", description = "AT, RT 재발급을 진행합니다.")
    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ){
        TokenIssueResult<ReissueResponse> result = authService.reissue(refreshToken);
        cookieUtil.setRtCookie(response, result.refreshToken());

        return ApiResponse.onSuccess(AuthSuccessCode.REISSUE_OK, result.body());
    }

    @Operation(summary = "로그아웃", description = "로그아웃을 진행합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletResponse response
            ){
        authService.logout(userDetails.getMemberId(), bearerToken);
        cookieUtil.expireRtCookie(response);

        return ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_OK);
    }
}
