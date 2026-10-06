package com.ceos24.cgv.domain.user.controller;


import com.ceos24.cgv.domain.user.dto.request.LoginRequest;
import com.ceos24.cgv.domain.user.dto.request.SignUpRequest;
import com.ceos24.cgv.domain.user.dto.response.LoginResponse;
import com.ceos24.cgv.domain.user.service.AuthService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "인증 API")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입 API", description = "새로운 유저를 등록합니다.")
    @PostMapping("/signup")
    public ApiResponse<Void> signup(@RequestBody @Valid SignUpRequest request) {
        return authService.signup(request);
    }
    @Operation(
            summary = "로그인",
            description = "아이디와 비밀번호를 검증하고 Access Token을 발급합니다."
    )
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @RequestBody @Valid LoginRequest request
    ) {
        LoginResponse response = authService.login(request);

        return ApiResponse.onSuccess(
                "로그인에 성공했습니다.",
                response
        );
    }


}
