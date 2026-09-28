package com.cgvclone.cgv.domain.auth.dto;

public record LoginResponse(
        Long userId,
        String email
) {
}
