package com.ceos24.cgv.domain.user.dto;

import com.ceos24.cgv.domain.user.entity.Role;
import com.ceos24.cgv.global.security.AuthUser;

public record AdminCheckResponse(
        Long userId,
        Role role
) {
    public static AdminCheckResponse from(AuthUser authUser) {
        return new AdminCheckResponse(authUser.userId(), authUser.role());
    }
}
