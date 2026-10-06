package com.spring_cgv_24th.domain.auth.controller;

import com.spring_cgv_24th.domain.auth.dto.AuthReqDTO;
import com.spring_cgv_24th.domain.auth.dto.AuthResDTO;
import com.spring_cgv_24th.domain.auth.service.AuthService;
import com.spring_cgv_24th.global.response.ApiResponse;
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

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResDTO.SignUpResDTO> signUp(
            @Valid @RequestBody AuthReqDTO.SignUpReqDTO request) {
        return ApiResponse.onCreated(authService.signUp(request));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ApiResponse<AuthResDTO.LoginResDTO> login(
            @Valid @RequestBody AuthReqDTO.LoginReqDTO request) {
        return ApiResponse.onSuccess(authService.login(request));
    }

    @Operation(summary = "Access Token 재발급",
            description = "요청 본문의 Refresh Token으로 재발급합니다. Access Token 인증은 필요하지 않습니다.")
    @PostMapping("/refresh")
    public ApiResponse<AuthResDTO.RefreshResDTO> refresh(
            @Valid @RequestBody AuthReqDTO.RefreshReqDTO request) {
        return ApiResponse.onSuccess(authService.refresh(request));
    }

    @Operation(summary = "로그아웃",
            description = "요청 본문의 Refresh Token을 폐기합니다. Access Token 인증은 필요하지 않으며, "
                    + "이미 발급된 Access Token은 만료까지 유효합니다.")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody AuthReqDTO.LogoutReqDTO request) {
        authService.logout(request);
        return ApiResponse.onSuccess(null);
    }
}
