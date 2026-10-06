package com.ceos24.spring_cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @Schema(description = "이메일", example = "example@gmail.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식에 맞게 입력해주세요.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
){}
