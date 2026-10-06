package com.ceos24.spring_cgv.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record SignUpRequest(

        @Schema(description = "회원 이름", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자를 넘을 수 없습니다.")
        String name,

        @Schema(description = "이메일", example = "example@gmail.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식에 맞게 입력해주세요.")
        String email,

        @Schema(description = "비밀번호", example = "qwer1234$")
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 7, max = 20, message = "비밀번호는 7자 이상 20자 이하여야 합니다.")
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*]).+$",
                message = "비밀번호는 영문자, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인은 필수입니다.")
        String passwordConfirm
){
    @AssertTrue(message = "비밀번호와 비밀번호 확인이 일치하지 않습니다.")
    @Schema(hidden = true)
    public boolean isPasswordMatching(){

        if (password == null || passwordConfirm == null){
            return true;
        }
        return password.equals(passwordConfirm);
    }
}
