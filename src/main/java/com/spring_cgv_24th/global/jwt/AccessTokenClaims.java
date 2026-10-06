package com.spring_cgv_24th.global.jwt;

import com.spring_cgv_24th.domain.member.enums.MemberRole;

public record AccessTokenClaims(
        Long memberId,
        MemberRole role
) {
}
