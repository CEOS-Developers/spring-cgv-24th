package com.ceos.cgv.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

public class PublicApiRequestMatcher implements RequestMatcher {
    @Override
    public boolean matches(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod())
                && (path.equals("/api/v1/auth/signup") || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout"))) {
            return true;
        }
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        if (path.equals("/swagger-ui.html") || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.equals("/actuator/health")) {
            return true;
        }
        return path.equals("/api/v1/movies")
                || path.matches("/api/v1/movies/[^/]+")
                || path.matches("/api/v1/movies/[^/]+/screenings")
                || path.matches("/api/v1/screenings/[^/]+/seats")
                || path.equals("/api/v1/cinemas")
                || path.matches("/api/v1/cinemas/[^/]+")
                || path.equals("/api/v1/products");
    }
}
