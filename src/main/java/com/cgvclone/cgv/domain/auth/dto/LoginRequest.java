package com.cgvclone.cgv.domain.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}
