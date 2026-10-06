package com.ceos24.cgv.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// role 필드를 두지 않는다. 본문에 "role"이 와도 바인딩될 곳이 없어 버려진다.
public record SignupRequest(
        // MySQL 기본 collation은 대소문자를 구분하지 않아 "Abc"와 "abc"가 unique에서 충돌한다.
        // 소문자로 제한해 검증 규칙과 DB 판정을 일치시킨다.
        @NotNull
        @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "영문 소문자와 숫자 4~20자여야 합니다.")
        String loginId,

        // BCrypt는 72바이트를 넘는 입력을 거부한다. 한글은 글자당 3바이트라 길이 제한만으로는
        // 막을 수 없어 공백 없는 ASCII로 제한한다.
        @NotNull
        @Pattern(regexp = "^[\\x21-\\x7E]{8,64}$", message = "공백 없는 영문·숫자·특수문자 8~64자여야 합니다.")
        String password,

        @NotBlank @Size(max = 50)
        String name,

        @NotNull @Past
        LocalDate birthDate,

        @NotBlank @Email @Size(max = 100)
        String email,

        @NotNull
        @Pattern(regexp = "^01[016789][0-9]{7,8}$", message = "하이픈 없이 숫자만 입력해야 합니다. 예: 01012345678")
        String phoneNumber
) {
}
