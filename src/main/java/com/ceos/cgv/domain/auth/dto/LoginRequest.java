package com.ceos.cgv.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {
    @Override
    public String toString() {
        return "LoginRequest[loginId=" + loginId + ", password=[REDACTED]]";
    }
}
