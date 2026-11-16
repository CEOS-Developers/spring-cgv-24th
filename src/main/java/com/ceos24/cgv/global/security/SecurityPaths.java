package com.ceos24.cgv.global.security;

public final class SecurityPaths {

    public static final String[] JWT_EXCLUDED_PATHS = {
            "/api/auth/login",
            "/api/auth/signup",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/error"
    };

    private SecurityPaths() {
    }
}
