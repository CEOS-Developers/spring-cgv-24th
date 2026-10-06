package com.ceos24.cgv.domain.user.controller;

import com.ceos24.cgv.domain.user.dto.request.LoginRequest;
import com.ceos24.cgv.domain.user.dto.response.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {

    @Operation(
            summary = "자체 로그인",
            description = """
                    아이디와 비밀번호로 로그인합니다.
                    로그인에 성공하면 Access Token과 Refresh Token을 반환합니다.
                    실제 인증 처리는 Spring Security의 LoginFilter가 담당합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치")
    })
    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public LoginResponse login(@RequestBody LoginRequest request) {
        // 정상적인 요청이라면 LoginFilter에서 먼저 처리하므로 실행되지 않습니다.
        throw new IllegalStateException("LoginFilter에서 처리되어야 하는 요청입니다.");
    }
}
