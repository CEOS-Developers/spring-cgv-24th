package com.ceos24.springboot.auth.dto;

import com.ceos24.springboot.user.domain.Role;
import com.ceos24.springboot.user.domain.User;

public record SignupResponse(
        Long userId,
        String email,
        Role role
) {
    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(),
                user.getEmail(),
                user.getRole()
        );
    }
}
