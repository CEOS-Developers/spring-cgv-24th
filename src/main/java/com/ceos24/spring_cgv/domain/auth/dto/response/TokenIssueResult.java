package com.ceos24.spring_cgv.domain.auth.dto.response;

public record TokenIssueResult<T>(
        T body,
        String refreshToken
) {}
