package com.ceos24.cgv.global.security;

import com.ceos24.cgv.domain.user.entity.Role;

// 검증을 통과한 토큰에서만 만들어진다. 요청마다 DB를 보지 않으므로 비밀번호를 가질 이유가 없다.
public record AuthUser(
        Long userId,
        Role role
) {
}
