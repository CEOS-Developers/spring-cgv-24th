package com.ceos24.springboot.auth.dto;

public record LoginRequest(
        String email,
        String password
) {
}