package com.ceos24.cgv.domain.auth.dto.request;

public record SignUpRequest(
        String name,

        String email,

        String loginId,

        String password
) {}
