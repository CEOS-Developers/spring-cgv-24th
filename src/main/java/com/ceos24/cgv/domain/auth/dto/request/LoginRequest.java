package com.ceos24.cgv.domain.auth.dto.request;

public record LoginRequest (
        String loginId,
        String password
) {}
