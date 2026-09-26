package com.ceos.cgv.domain.auth.dto;

import com.ceos.cgv.domain.user.entity.User;

public record SignupResponse(
        Long userId,
        String loginId,
        String name,
        String email
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId(), user.getName(), user.getEmail());
    }
}
