package com.ceos24.cgv.global.security.jwt;

import com.ceos24.cgv.domain.user.enums.UserRole;

public record AccessTokenInfo(
        Long userId,
        UserRole role
) {
}
