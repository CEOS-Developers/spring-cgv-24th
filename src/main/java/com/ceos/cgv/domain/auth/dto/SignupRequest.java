package com.ceos.cgv.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{4,20}") String loginId,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 100) String email,
        @NotBlank String password
) {
    public SignupRequest {
        if (name != null) {
            name = name.trim();
        }
        if (email != null) {
            email = email.trim();
        }
    }

    @Override
    public String toString() {
        return "SignupRequest[loginId=" + loginId
                + ", name=" + name
                + ", email=" + email
                + ", password=[REDACTED]]";
    }
}
