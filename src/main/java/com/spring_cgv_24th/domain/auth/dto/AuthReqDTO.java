package com.spring_cgv_24th.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public class AuthReqDTO {

    private AuthReqDTO() {
    }

    public record SignUpReqDTO(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 254, message = "이메일은 254자 이하이어야 합니다.")
            String email,

            @NotBlank(message = "이름은 필수입니다.")
            @Size(max = 50, message = "이름은 50자 이하이어야 합니다.")
            String name,

            @NotBlank(message = "비밀번호는 필수입니다.")
            @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
            String password
    ) {
        // BCrypt의 최대 입력 길이는 문자 수가 아닌 UTF-8 바이트 수로 검사한다.
        @JsonIgnore
        @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하이어야 합니다.")
        public boolean isPasswordByteLengthValid() {
            // 누락된 비밀번호는 @NotBlank가 검증한다.
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    public record LoginReqDTO(
            @NotBlank(message = "이메일은 필수입니다.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email,

            @NotBlank(message = "비밀번호는 필수입니다.")
            String password
    ) {
    }

    public record RefreshReqDTO(
            @NotBlank(message = "Refresh Token은 필수입니다.")
            String refreshToken
    ) {
    }

    public record LogoutReqDTO(
            @NotBlank(message = "Refresh Token은 필수입니다.")
            String refreshToken
    ) {
    }
}
