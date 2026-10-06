package com.spring_cgv_24th.domain.auth.dto;

import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.enums.MemberRole;

public class AuthResDTO {

    private AuthResDTO() {
    }

    public record SignUpResDTO(
            Long memberId,
            String email,
            String name,
            MemberRole role
    ) {
        public static SignUpResDTO from(Member member) {
            return new SignUpResDTO(
                    member.getId(),
                    member.getEmail(),
                    member.getName(),
                    member.getRole());
        }
    }

    public record LoginResDTO(
            String accessToken,
            String refreshToken
    ) {
        public static LoginResDTO from(String accessToken, String refreshToken) {
            return new LoginResDTO(accessToken, refreshToken);
        }
    }

    public record RefreshResDTO(
            String accessToken
    ) {
        public static RefreshResDTO from(String accessToken) {
            return new RefreshResDTO(accessToken);
        }
    }
}
