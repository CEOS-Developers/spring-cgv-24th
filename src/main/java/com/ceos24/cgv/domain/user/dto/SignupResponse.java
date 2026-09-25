package com.ceos24.cgv.domain.user.dto;

import com.ceos24.cgv.domain.user.entity.User;

public record SignupResponse(
        Long userId,
        String loginId
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(user.getId(), user.getLoginId());
    }
}
